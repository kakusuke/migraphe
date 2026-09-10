package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.Fingerprinter;
import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryUpgrade;
import io.github.kakusuke.migraphe.api.history.UpgradeContext;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Covers the detect-then-apply schema-step mechanism driving {@link JdbcHistoryRepository}. */
class JdbcHistoryRepositorySchemaStepsTest {

    /**
     * Builds an target for a named in-memory database. {@code DB_CLOSE_DELAY=-1} keeps the database
     * alive between connections; without it each closed connection discards the schema and every
     * detection query would report "not applied".
     */
    private JdbcTarget target(String dbName) {
        return JdbcTarget.create(
                "testdb",
                "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1",
                "sa",
                "",
                "org.h2.Driver",
                "H2");
    }

    /**
     * A schema-only upgrade never reads the context, so one that answers nothing is enough to drive
     * it. Anything that did read it would fail loudly rather than quietly fold the wrong token.
     */
    private static final UpgradeContext NO_CONTEXT =
            new UpgradeContext() {
                @Override
                public MigrationGraphView definitions() {
                    throw new UnsupportedOperationException(
                            "a schema-only step reads no definitions");
                }

                @Override
                public Fingerprinter fingerprinterFor(NodeId nodeId) {
                    throw new UnsupportedOperationException("a schema-only step folds nothing");
                }
            };

    /** Runs the repository's upgrades the way the upgrade command does: pending ones, in order. */
    private void applyEveryUpgrade(JdbcHistoryRepository repository) {
        for (HistoryUpgrade upgrade : repository.upgrades()) {
            if (upgrade.isPending()) {
                upgrade.apply(NO_CONTEXT);
            }
        }
    }

    private void createPreUpgradeTable(JdbcTarget target) throws Exception {
        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(
                    """
                    CREATE TABLE migraphe_history (
                        id VARCHAR(64) PRIMARY KEY,
                        node_id VARCHAR(255) NOT NULL,
                        environment_id VARCHAR(255) NOT NULL,
                        direction VARCHAR(10) NOT NULL,
                        status VARCHAR(10) NOT NULL,
                        executed_at TIMESTAMP NOT NULL,
                        description TEXT,
                        serialized_down_task TEXT,
                        duration_ms BIGINT,
                        error_message TEXT
                    )
                    """);
        }
    }

    private boolean tableExists(JdbcTarget target, String table) throws Exception {
        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT 1 FROM information_schema.tables WHERE table_schema ="
                                        + " SCHEMA() AND UPPER(table_name) = '"
                                        + table
                                        + "'")) {
            return rs.next();
        }
    }

    private boolean columnExists(JdbcTarget target, String column) throws Exception {
        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT 1 FROM information_schema.columns WHERE table_schema ="
                                        + " SCHEMA() AND UPPER(table_name) = 'MIGRAPHE_HISTORY' AND"
                                        + " UPPER(column_name) = '"
                                        + column
                                        + "'")) {
            return rs.next();
        }
    }

    @Test
    @DisplayName("検出できたステップの適用SQLは実行されない")
    void skipsStepWhenDetected() throws Exception {
        JdbcTarget target = target("steps_skip");
        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE step_probe (id INT)");
        }
        var repository = new JdbcHistoryRepository(target, "/schema-steps/skip-when-detected.sql");

        // The apply statement has no IF NOT EXISTS, so it would fail if the step were not skipped.
        assertThatCode(repository::initialize).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("検出できないステップは適用される")
    void appliesStepWhenNotDetected() throws Exception {
        JdbcTarget target = target("steps_apply");
        var repository = new JdbcHistoryRepository(target, "/schema-steps/skip-when-detected.sql");

        repository.initialize();

        assertThat(tableExists(target, "STEP_PROBE")).isTrue();
    }

    @Test
    @DisplayName("適用が競合で失敗しても、再検出で適用済みなら飲み込む")
    void swallowsFailureWhenStepBecameApplied() throws Exception {
        JdbcTarget target = target("steps_race");
        // The resource applies the same CREATE TABLE twice: the second failure stands in for a
        // competing process having applied the step between our detection and our apply.
        var repository = new JdbcHistoryRepository(target, "/schema-steps/losing-race.sql");

        assertThatCode(repository::initialize).doesNotThrowAnyException();

        assertThat(tableExists(target, "RACE_TARGET")).isTrue();
    }

    @Test
    @DisplayName("適用が失敗し、再検出でも未適用なら例外を投げる")
    void propagatesFailureWhenStepStillMissing() {
        JdbcTarget target = target("steps_fail");
        var repository = new JdbcHistoryRepository(target, "/schema-steps/apply-fails.sql");

        assertThatThrownBy(repository::initialize)
                .isInstanceOf(JdbcException.class)
                .hasMessageContaining("never applied");
    }

    @Test
    @DisplayName("検出SQL自体の失敗は未適用に丸めず伝播させる")
    void propagatesCheckFailure() {
        JdbcTarget target = target("steps_broken_check");
        var repository = new JdbcHistoryRepository(target, "/schema-steps/broken-check.sql");

        assertThatThrownBy(repository::initialize)
                .isInstanceOf(JdbcException.class)
                .hasMessageContaining("broken detection");

        // The apply statement must not have run.
        assertThatCode(() -> assertThat(tableExists(target, "UNREACHABLE")).isFalse())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("既定リソースの作成ステップは検出SQLを持たず、initialize() は冪等")
    void defaultResourceIsIdempotent() throws Exception {
        // The shipped creation step carries no detection query: it leans on IF NOT EXISTS, which no
        // same-named table in another schema can confuse. Idempotency therefore rests on the
        // statements themselves, so assert it directly rather than trusting a detection query.
        // Later steps do carry one — ALTER TABLE has no portable conditional form — and they name
        // the current schema through a bound parameter instead of matching on table name alone.
        JdbcTarget target = target("steps_idempotent");
        String resource =
                new String(
                        getClass()
                                .getResourceAsStream(
                                        "/io/github/kakusuke/migraphe/jdbc/schema/init_history_table.sql")
                                .readAllBytes(),
                        StandardCharsets.UTF_8);
        var steps = SchemaStepParser.parse(resource);
        assertThat(steps).isNotEmpty();
        assertThat(steps.get(0).checkSql()).isNull();
        assertThat(steps.subList(1, steps.size()))
                .allSatisfy(step -> assertThat(step.checkSql()).contains("?"));

        var repository = new JdbcHistoryRepository(target);
        repository.initialize();

        assertThatCode(repository::initialize).doesNotThrowAnyException();
        assertThat(tableExists(target, "MIGRAPHE_HISTORY")).isTrue();
    }

    @Test
    @DisplayName("新規作成したテーブルは target_id 列を持つ")
    void freshTableUsesTargetId() throws Exception {
        JdbcTarget target = target("steps_fresh_target_id");
        new JdbcHistoryRepository(target).initialize();

        assertThat(columnExists(target, "TARGET_ID")).isTrue();
        assertThat(columnExists(target, "ENVIRONMENT_ID")).isFalse();
    }

    @Test
    @DisplayName("upgrades() は旧テーブルを現在の形へ運び、済んだものは pending でなくなる")
    void upgradesCarryAnOlderTableToTheCurrentShape() throws Exception {
        JdbcTarget target = target("steps_upgrades");
        createPreUpgradeTable(target);
        JdbcHistoryRepository repository = new JdbcHistoryRepository(target);

        repository.initialize();

        List<HistoryUpgrade> upgrades = repository.upgrades();
        assertThat(upgrades).hasSize(7);
        assertThat(upgrades).allMatch(HistoryUpgrade::isPending);
        // The row filling comes last, so every column it writes exists by the time it runs.
        assertThat(upgrades.get(6).description())
                .isEqualTo("fill what the definitions still declare");

        for (HistoryUpgrade upgrade : upgrades) {
            upgrade.apply(NO_CONTEXT);
        }

        assertThat(upgrades).noneMatch(HistoryUpgrade::isPending);
        assertThat(columnExists(target, "TARGET_ID")).isTrue();
        assertThat(columnExists(target, "ENVIRONMENT_ID")).isFalse();
        assertThat(columnExists(target, "FINGERPRINT")).isTrue();
        assertThat(columnExists(target, "PLUGIN_METADATA")).isTrue();
        assertThat(columnExists(target, "DEPENDENCIES")).isTrue();
        assertThat(columnExists(target, "ORIGIN")).isTrue();
        assertThat(columnExists(target, "NO_WAY_BACK")).isTrue();
    }

    @Test
    @DisplayName("environment_id を持つ旧テーブルは target_id にリネームされ、既存行が保持される")
    void renamesTheLegacyColumnKeepingRows() throws Exception {
        JdbcTarget target = target("steps_legacy_rename");
        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(
                    """
                    CREATE TABLE migraphe_history (
                        id VARCHAR(64) PRIMARY KEY,
                        node_id VARCHAR(255) NOT NULL,
                        environment_id VARCHAR(255) NOT NULL,
                        direction VARCHAR(10) NOT NULL,
                        status VARCHAR(10) NOT NULL,
                        executed_at TIMESTAMP NOT NULL,
                        description TEXT,
                        serialized_down_task TEXT,
                        duration_ms BIGINT,
                        error_message TEXT
                    )
                    """);
            stmt.execute(
                    "INSERT INTO migraphe_history VALUES ('legacy-1', 'node1', 'testdb', 'UP',"
                            + " 'SUCCESS', CURRENT_TIMESTAMP, 'legacy row', NULL, 1, NULL)");
        }

        applyEveryUpgrade(new JdbcHistoryRepository(target));

        assertThat(columnExists(target, "TARGET_ID")).isTrue();
        assertThat(columnExists(target, "ENVIRONMENT_ID")).isFalse();
        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT id, target_id FROM migraphe_history WHERE id ="
                                        + " 'legacy-1'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("target_id")).isEqualTo("testdb");
        }
    }

    @Test
    @DisplayName("fingerprint 列は新規テーブルにも既存テーブルにも作られ、再実行に耐える")
    void addsFingerprintColumn() throws Exception {
        JdbcTarget fresh = target("steps_fingerprint_fresh");
        new JdbcHistoryRepository(fresh).initialize();

        assertThat(columnExists(fresh, "FINGERPRINT")).isTrue();

        JdbcTarget existing = target("steps_fingerprint_existing");
        try (Connection conn = existing.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(
                    """
                    CREATE TABLE migraphe_history (
                        id VARCHAR(64) PRIMARY KEY,
                        node_id VARCHAR(255) NOT NULL,
                        target_id VARCHAR(255) NOT NULL,
                        direction VARCHAR(10) NOT NULL,
                        status VARCHAR(10) NOT NULL,
                        executed_at TIMESTAMP NOT NULL,
                        description TEXT,
                        serialized_down_task TEXT,
                        duration_ms BIGINT,
                        error_message TEXT
                    )
                    """);
            stmt.execute(
                    "INSERT INTO migraphe_history VALUES ('pre-1', 'node1', 'testdb', 'UP',"
                            + " 'SUCCESS', CURRENT_TIMESTAMP, 'row from before the column', NULL,"
                            + " 1, NULL)");
        }

        var repository = new JdbcHistoryRepository(existing);
        applyEveryUpgrade(repository);

        assertThat(columnExists(existing, "FINGERPRINT")).isTrue();
        assertThatCode(() -> applyEveryUpgrade(repository)).doesNotThrowAnyException();
        try (Connection conn = existing.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT fingerprint FROM migraphe_history WHERE id = 'pre-1'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("fingerprint")).isNull();
        }
    }

    @Test
    @DisplayName("リネーム済みのテーブルに再実行しても壊れない")
    void renameStepIsIdempotent() throws Exception {
        JdbcTarget target = target("steps_rename_idempotent");
        var repository = new JdbcHistoryRepository(target);
        repository.initialize();

        assertThatCode(repository::initialize).doesNotThrowAnyException();
        assertThat(columnExists(target, "TARGET_ID")).isTrue();
    }

    @Test
    @DisplayName("検出SQLの ? に現在のスキーマが束縛される")
    void bindsTheCurrentSchemaToTheDetectionParameter() throws Exception {
        JdbcTarget target = target("steps_qualified_hit");
        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE step_probe (id INT)");
        }
        var repository = new JdbcHistoryRepository(target, "/schema-steps/qualified-check.sql");

        // The apply statement has no IF NOT EXISTS, so an unbound parameter or a missed
        // detection would surface here.
        assertThatCode(repository::initialize).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("別スキーマの同名テーブルは検出とみなさない")
    void doesNotMistakeASameNamedTableInAnotherSchema() throws Exception {
        JdbcTarget target = target("steps_qualified_miss");
        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA other");
            stmt.execute("CREATE TABLE other.step_probe (id INT)");
        }
        var repository = new JdbcHistoryRepository(target, "/schema-steps/qualified-check.sql");

        repository.initialize();

        // Unqualified detection would have found other.step_probe and skipped creation.
        assertThat(tableExists(target, "STEP_PROBE")).isTrue();
    }
}
