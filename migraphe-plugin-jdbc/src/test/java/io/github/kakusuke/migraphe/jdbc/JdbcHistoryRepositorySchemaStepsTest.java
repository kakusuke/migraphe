package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Covers the detect-then-apply schema-step mechanism driving {@link JdbcHistoryRepository}. */
class JdbcHistoryRepositorySchemaStepsTest {

    /**
     * Builds an environment for a named in-memory database. {@code DB_CLOSE_DELAY=-1} keeps the
     * database alive between connections; without it each closed connection discards the schema and
     * every detection query would report "not applied".
     */
    private JdbcEnvironment env(String dbName) {
        return JdbcEnvironment.create(
                "testdb",
                "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1",
                "sa",
                "",
                "org.h2.Driver",
                "H2");
    }

    private boolean tableExists(JdbcEnvironment env, String table) throws Exception {
        try (Connection conn = env.createConnection();
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

    @Test
    @DisplayName("検出できたステップの適用SQLは実行されない")
    void skipsStepWhenDetected() throws Exception {
        JdbcEnvironment env = env("steps_skip");
        try (Connection conn = env.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE step_probe (id INT)");
        }
        var repository = new JdbcHistoryRepository(env, "/schema-steps/skip-when-detected.sql");

        // The apply statement has no IF NOT EXISTS, so it would fail if the step were not skipped.
        assertThatCode(repository::initialize).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("検出できないステップは適用される")
    void appliesStepWhenNotDetected() throws Exception {
        JdbcEnvironment env = env("steps_apply");
        var repository = new JdbcHistoryRepository(env, "/schema-steps/skip-when-detected.sql");

        repository.initialize();

        assertThat(tableExists(env, "STEP_PROBE")).isTrue();
    }

    @Test
    @DisplayName("適用が競合で失敗しても、再検出で適用済みなら飲み込む")
    void swallowsFailureWhenStepBecameApplied() throws Exception {
        JdbcEnvironment env = env("steps_race");
        // The resource applies the same CREATE TABLE twice: the second failure stands in for a
        // competing process having applied the step between our detection and our apply.
        var repository = new JdbcHistoryRepository(env, "/schema-steps/losing-race.sql");

        assertThatCode(repository::initialize).doesNotThrowAnyException();

        assertThat(tableExists(env, "RACE_TARGET")).isTrue();
    }

    @Test
    @DisplayName("適用が失敗し、再検出でも未適用なら例外を投げる")
    void propagatesFailureWhenStepStillMissing() {
        JdbcEnvironment env = env("steps_fail");
        var repository = new JdbcHistoryRepository(env, "/schema-steps/apply-fails.sql");

        assertThatThrownBy(repository::initialize)
                .isInstanceOf(JdbcException.class)
                .hasMessageContaining("never applied");
    }

    @Test
    @DisplayName("検出SQL自体の失敗は未適用に丸めず伝播させる")
    void propagatesCheckFailure() {
        JdbcEnvironment env = env("steps_broken_check");
        var repository = new JdbcHistoryRepository(env, "/schema-steps/broken-check.sql");

        assertThatThrownBy(repository::initialize)
                .isInstanceOf(JdbcException.class)
                .hasMessageContaining("broken detection");

        // The apply statement must not have run.
        assertThatCode(() -> assertThat(tableExists(env, "UNREACHABLE")).isFalse())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("検出SQLを持たない既定リソースでも initialize() は冪等")
    void defaultResourceIsIdempotent() throws Exception {
        // The shipped creation steps carry no detection query: they lean on IF NOT EXISTS, which no
        // same-named table in another schema can confuse. Idempotency therefore rests on the
        // statements themselves, so assert it directly rather than trusting a detection query.
        JdbcEnvironment env = env("steps_idempotent");
        String resource =
                new String(
                        getClass()
                                .getResourceAsStream(
                                        "/io/github/kakusuke/migraphe/jdbc/schema/init_history_table.sql")
                                .readAllBytes(),
                        StandardCharsets.UTF_8);
        assertThat(SchemaStepParser.parse(resource))
                .isNotEmpty()
                .allSatisfy(step -> assertThat(step.checkSql()).isNull());

        var repository = new JdbcHistoryRepository(env);
        repository.initialize();

        assertThatCode(repository::initialize).doesNotThrowAnyException();
        assertThat(tableExists(env, "MIGRAPHE_HISTORY")).isTrue();
    }
}
