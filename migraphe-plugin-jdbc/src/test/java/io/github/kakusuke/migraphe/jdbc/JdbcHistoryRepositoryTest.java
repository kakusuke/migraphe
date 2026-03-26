package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.ExecutionStatus;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcHistoryRepositoryTest {

    private JdbcEnvironment env;
    private JdbcHistoryRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        env =
                JdbcEnvironment.create(
                        "testdb",
                        "jdbc:h2:mem:history_test;DB_CLOSE_DELAY=-1",
                        "sa",
                        "",
                        "org.h2.Driver",
                        "H2");
        // Clean up
        try (Connection conn = env.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS migraphe_history");
        }
        repository = new JdbcHistoryRepository(env);
    }

    @Test
    void initializeCreatesTable() throws Exception {
        repository.initialize();

        try (Connection conn = env.createConnection();
                Statement stmt = conn.createStatement();
                var rs =
                        stmt.executeQuery(
                                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES"
                                        + " WHERE TABLE_NAME = 'MIGRAPHE_HISTORY'")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void recordAndRetrieve() {
        repository.initialize();

        var record =
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS);
        repository.record(record);

        var latest = repository.findLatestRecord(NodeId.of("node1"), EnvironmentId.of("testdb"));
        assertThat(latest).isNotNull();
        assertThat(latest.id()).isEqualTo("rec1");
        assertThat(latest.nodeId()).isEqualTo(NodeId.of("node1"));
        assertThat(latest.direction()).isEqualTo(ExecutionDirection.UP);
        assertThat(latest.status()).isEqualTo(ExecutionStatus.SUCCESS);
    }

    @Test
    void wasExecutedReturnsTrueForSuccessfulUp() {
        repository.initialize();

        var record =
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS);
        repository.record(record);

        assertThat(repository.wasExecuted(NodeId.of("node1"), EnvironmentId.of("testdb"))).isTrue();
    }

    @Test
    void wasExecutedReturnsFalseForDown() {
        repository.initialize();

        var upRecord =
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS);
        repository.record(upRecord);

        var downRecord =
                createRecord(
                        "rec2",
                        "node1",
                        "testdb",
                        ExecutionDirection.DOWN,
                        ExecutionStatus.SUCCESS);
        repository.record(downRecord);

        assertThat(repository.wasExecuted(NodeId.of("node1"), EnvironmentId.of("testdb")))
                .isFalse();
    }

    @Test
    void wasExecutedReturnsFalseForFailure() {
        repository.initialize();

        var record =
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.FAILURE);
        repository.record(record);

        assertThat(repository.wasExecuted(NodeId.of("node1"), EnvironmentId.of("testdb")))
                .isFalse();
    }

    @Test
    void wasExecutedReturnsFalseForUnknownNode() {
        repository.initialize();
        assertThat(repository.wasExecuted(NodeId.of("unknown"), EnvironmentId.of("testdb")))
                .isFalse();
    }

    @Test
    void executedNodesReturnsSuccessfulUpNodes() {
        repository.initialize();

        repository.record(
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS));
        repository.record(
                createRecord(
                        "rec2", "node2", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS));
        repository.record(
                createRecord(
                        "rec3", "node3", "testdb", ExecutionDirection.UP, ExecutionStatus.FAILURE));

        List<NodeId> nodes = repository.executedNodes(EnvironmentId.of("testdb"));
        assertThat(nodes).containsExactly(NodeId.of("node1"), NodeId.of("node2"));
    }

    @Test
    void findLatestRecordReturnsNullForUnknown() {
        repository.initialize();
        assertThat(repository.findLatestRecord(NodeId.of("unknown"), EnvironmentId.of("testdb")))
                .isNull();
    }

    @Test
    void allRecordsReturnsInOrder() {
        repository.initialize();

        repository.record(
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS));
        repository.record(
                createRecord(
                        "rec2", "node2", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS));

        List<ExecutionRecord> records = repository.allRecords(EnvironmentId.of("testdb"));
        assertThat(records).hasSize(2);
        assertThat(records.get(0).id()).isEqualTo("rec1");
        assertThat(records.get(1).id()).isEqualTo("rec2");
    }

    @Test
    void customSchemaResourcePath() {
        var customRepo =
                new JdbcHistoryRepository(
                        env, "/io/github/kakusuke/migraphe/jdbc/schema/init_history_table.sql");
        customRepo.initialize();
        customRepo.record(
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS));
        assertThat(customRepo.wasExecuted(NodeId.of("node1"), EnvironmentId.of("testdb"))).isTrue();
    }

    private ExecutionRecord createRecord(
            String id,
            String nodeId,
            String envId,
            ExecutionDirection direction,
            ExecutionStatus status) {
        return new ExecutionRecord(
                id,
                NodeId.of(nodeId),
                EnvironmentId.of(envId),
                direction,
                status,
                Instant.now(),
                "test description",
                direction == ExecutionDirection.UP ? "DOWN SQL" : null,
                100L,
                status == ExecutionStatus.FAILURE ? "test error" : null);
    }
}
