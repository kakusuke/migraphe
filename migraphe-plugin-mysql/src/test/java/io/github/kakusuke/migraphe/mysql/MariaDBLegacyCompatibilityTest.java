package io.github.kakusuke.migraphe.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.ExecutionStatus;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.jdbc.JdbcHistoryRepository;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Compatibility tests against a legacy MariaDB server.
 *
 * <p>MariaDB 10.1 reproduces the two constraints of the 5.5 generation that modern servers no
 * longer impose: it defaults to {@code innodb_file_format=Antelope} with {@code
 * innodb_large_prefix=0}, so InnoDB enforces the 767-byte index key prefix limit, and it predates
 * window functions (added in MariaDB 10.2). The server even reports itself as {@code
 * 5.5.5-10.1.48-MariaDB}. It is used instead of {@code mariadb:5.5} because only 10.1 and later
 * publish arm64 images.
 *
 * <p>The image is driven through {@link MySQLContainer} rather than {@code MariaDBContainer} so the
 * container hands out a {@code jdbc:mysql://} URL, which is what {@link MySQLEnvironment}'s fixed
 * MySQL driver can open.
 */
@Testcontainers
class MariaDBLegacyCompatibilityTest {

    @Container
    static MySQLContainer<?> mariadb =
            new MySQLContainer<>(
                            DockerImageName.parse("mariadb:10.1")
                                    .asCompatibleSubstituteFor("mysql"))
                    .withDatabaseName("migraphe_test");

    private static final String MYSQL_SCHEMA_RESOURCE =
            "/io/github/kakusuke/migraphe/mysql/schema/init_history_table.sql";

    private MySQLEnvironment environment;
    private HistoryRepository historyRepo;

    @BeforeEach
    void setUp() throws Exception {
        environment =
                MySQLEnvironment.create(
                        "test", mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword());
        historyRepo = new JdbcHistoryRepository(environment, MYSQL_SCHEMA_RESOURCE);

        // The container is shared by every test, so start each one from an empty history.
        try (Connection conn = environment.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS migraphe_history");
        }
    }

    @Test
    void serverEnforcesLegacyInnoDbKeyLimit() throws Exception {
        // Canary: if a future image stops enforcing the 767-byte limit, the DDL test below would
        // silently stop proving anything.
        try (Connection conn = environment.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT @@innodb_large_prefix")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    void initializeCreatesUsableHistoryTableUnderLegacyKeyLimit() {
        assertThatCode(historyRepo::initialize).doesNotThrowAnyException();

        NodeId nodeId = NodeId.of("db1/create_table");
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        nodeId, environment.id(), "create table", "DROP TABLE users", 10));

        assertThat(historyRepo.wasExecuted(nodeId, environment.id())).isTrue();
    }

    @Test
    void nonAsciiNodeIdSurvivesRoundTrip() {
        // Task ids are derived from the task file path, so they may contain non-ASCII characters.
        // This is why the identifier columns keep the utf8mb4 charset instead of being narrowed to
        // ascii to fit the key limit.
        historyRepo.initialize();

        NodeId nodeId = NodeId.of("ユーザ作成");
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        nodeId, environment.id(), "ユーザ作成", "DROP TABLE users", 10));

        assertThat(historyRepo.wasExecuted(nodeId, environment.id())).isTrue();
        ExecutionRecord latest = historyRepo.findLatestRecord(nodeId, environment.id());
        assertThat(latest).isNotNull();
        assertThat(latest.nodeId()).isEqualTo(nodeId);
    }

    @Test
    void executedNodesWorksOnServerWithoutWindowFunctions() {
        // Window functions only arrived in MariaDB 10.2, so the query must not rely on them.
        historyRepo.initialize();

        NodeId applied = NodeId.of("db1/applied");
        NodeId rolledBack = NodeId.of("db1/rolled_back");
        NodeId failed = NodeId.of("db1/failed");

        // Timestamps are set explicitly, whole seconds apart: MariaDB reports itself as 5.5.5, so
        // Connector/J drops the fractional part and records written in the same second would tie.
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        historyRepo.record(record(applied, ExecutionDirection.UP, ExecutionStatus.SUCCESS, base));
        historyRepo.record(
                record(rolledBack, ExecutionDirection.UP, ExecutionStatus.SUCCESS, base));
        historyRepo.record(
                record(
                        rolledBack,
                        ExecutionDirection.DOWN,
                        ExecutionStatus.SUCCESS,
                        base.plusSeconds(2)));
        historyRepo.record(record(failed, ExecutionDirection.UP, ExecutionStatus.FAILURE, base));

        assertThat(historyRepo.executedNodes(environment.id())).containsExactly(applied);
    }

    @Test
    void initializeIsIdempotentOnThisServer() throws Exception {
        // The creation step has no detection query: it leans on CREATE TABLE IF NOT EXISTS, which
        // this 5.5-generation server must honour for initialize() to stay idempotent across the
        // up/down/status calls that all run it. The rename step that follows is guarded by a
        // detection query instead, so this also covers that query running on this server.
        historyRepo.initialize();

        assertThatCode(() -> historyRepo.initialize()).doesNotThrowAnyException();

        try (Connection conn = environment.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema"
                                        + " = DATABASE() AND table_name = 'migraphe_history'")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    // --- Ordering when executed_at ties -------------------------------------------------
    // This server reports itself as 5.5.5, so Connector/J drops fractional seconds and a down
    // immediately followed by an up shares a timestamp. Both directions are asserted: ordering
    // by executed_at alone leaves the winner to the storage engine, so one direction could pass
    // by luck while the other fails.

    @Test
    void sameSecondUpThenDownCountsAsRolledBack() {
        historyRepo.initialize();
        NodeId node = NodeId.of("db1/flip");
        Instant sameSecond = Instant.parse("2026-01-01T00:00:00Z");

        historyRepo.record(
                record(
                        generatedId(),
                        node,
                        ExecutionDirection.UP,
                        ExecutionStatus.SUCCESS,
                        sameSecond));
        historyRepo.record(
                record(
                        generatedId(),
                        node,
                        ExecutionDirection.DOWN,
                        ExecutionStatus.SUCCESS,
                        sameSecond));

        assertThat(historyRepo.wasExecuted(node, environment.id())).isFalse();
    }

    @Test
    void sameSecondDownThenUpCountsAsApplied() {
        historyRepo.initialize();
        NodeId node = NodeId.of("db1/flip");
        Instant sameSecond = Instant.parse("2026-01-01T00:00:00Z");

        historyRepo.record(
                record(
                        generatedId(),
                        node,
                        ExecutionDirection.DOWN,
                        ExecutionStatus.SUCCESS,
                        sameSecond));
        historyRepo.record(
                record(
                        generatedId(),
                        node,
                        ExecutionDirection.UP,
                        ExecutionStatus.SUCCESS,
                        sameSecond));

        assertThat(historyRepo.wasExecuted(node, environment.id())).isTrue();
    }

    @Test
    void sameSecondRollbackIsExcludedFromExecutedNodes() {
        historyRepo.initialize();
        NodeId node = NodeId.of("db1/flip");
        Instant sameSecond = Instant.parse("2026-01-01T00:00:00Z");

        historyRepo.record(
                record(
                        generatedId(),
                        node,
                        ExecutionDirection.UP,
                        ExecutionStatus.SUCCESS,
                        sameSecond));
        historyRepo.record(
                record(
                        generatedId(),
                        node,
                        ExecutionDirection.DOWN,
                        ExecutionStatus.SUCCESS,
                        sameSecond));

        assertThat(historyRepo.executedNodes(environment.id())).isEmpty();
    }

    @Test
    void renamesLegacyEnvironmentIdColumnWithChangeColumn() throws Exception {
        // RENAME COLUMN needs MariaDB 10.5.2, so the resource spells the rename as CHANGE COLUMN;
        // this server is where that choice has to hold.
        try (Connection conn = environment.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(
                    """
                    CREATE TABLE migraphe_history (
                        id VARCHAR(64) PRIMARY KEY,
                        node_id VARCHAR(255) NOT NULL,
                        environment_id VARCHAR(255) NOT NULL,
                        direction VARCHAR(10) NOT NULL,
                        status VARCHAR(10) NOT NULL,
                        executed_at TIMESTAMP(6) NOT NULL,
                        description TEXT,
                        serialized_down_task LONGTEXT,
                        duration_ms BIGINT,
                        error_message TEXT,
                        INDEX idx_migraphe_history_node_env (node_id(100), environment_id(60))
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            stmt.execute(
                    "INSERT INTO migraphe_history VALUES ('legacy-1', 'db1/legacy', 'test', 'UP',"
                            + " 'SUCCESS', NOW(6), 'legacy row', NULL, 1, NULL)");
        }

        historyRepo.initialize();

        assertThat(columnExists("target_id")).isTrue();
        assertThat(columnExists("environment_id")).isFalse();
        // The migrated row still reads back through the repository, index prefixes included.
        assertThat(historyRepo.wasExecuted(NodeId.of("db1/legacy"), environment.id())).isTrue();
    }

    private boolean columnExists(String column) throws Exception {
        try (Connection conn = environment.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT 1 FROM information_schema.columns WHERE table_schema ="
                                        + " DATABASE() AND table_name = 'migraphe_history' AND"
                                        + " column_name = '"
                                        + column
                                        + "'")) {
            return rs.next();
        }
    }

    /**
     * Returns an identifier from the production factory, so tied records carry real UUIDv7 values
     * in creation order rather than hand-written ones.
     */
    private String generatedId() {
        return ExecutionRecord.upSuccess(NodeId.of("seed"), environment.id(), "seed", null, 0).id();
    }

    private ExecutionRecord record(
            NodeId nodeId,
            ExecutionDirection direction,
            ExecutionStatus status,
            Instant executedAt) {
        return record(UUID.randomUUID().toString(), nodeId, direction, status, executedAt);
    }

    private ExecutionRecord record(
            String id,
            NodeId nodeId,
            ExecutionDirection direction,
            ExecutionStatus status,
            Instant executedAt) {
        return new ExecutionRecord(
                id,
                nodeId,
                environment.id(),
                direction,
                status,
                executedAt,
                "test description",
                direction == ExecutionDirection.UP ? "DROP TABLE t" : null,
                10L,
                status == ExecutionStatus.FAILURE ? "boom" : null);
    }
}
