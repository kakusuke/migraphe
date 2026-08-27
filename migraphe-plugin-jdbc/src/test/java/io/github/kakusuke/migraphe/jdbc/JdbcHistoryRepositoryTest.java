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

    // --- Ordering when executed_at ties -------------------------------------------------
    // MariaDB stores executed_at at second granularity (the MySQL driver drops fractional
    // seconds because the server reports itself as 5.5.5), so a down immediately followed by
    // an up shares a timestamp. The id then decides, and both directions are asserted: with
    // executed_at alone the winner is whatever the storage engine happens to return, so a
    // single direction could pass by luck.

    @Test
    void tiedTimestampsAreBrokenByIdWhenTheLatestIsDown() {
        repository.initialize();
        Instant sameSecond = Instant.parse("2026-08-20T10:00:00Z");

        repository.record(
                recordAt(
                        "00000000-0000-7000-8000-000000000001", sameSecond, ExecutionDirection.UP));
        repository.record(
                recordAt(
                        "00000000-0000-7000-8000-000000000002",
                        sameSecond,
                        ExecutionDirection.DOWN));

        assertThat(repository.wasExecuted(NodeId.of("node1"), EnvironmentId.of("testdb")))
                .isFalse();
    }

    @Test
    void tiedTimestampsAreBrokenByIdWhenTheLatestIsUp() {
        repository.initialize();
        Instant sameSecond = Instant.parse("2026-08-20T10:00:00Z");

        repository.record(
                recordAt(
                        "00000000-0000-7000-8000-000000000001",
                        sameSecond,
                        ExecutionDirection.DOWN));
        repository.record(
                recordAt(
                        "00000000-0000-7000-8000-000000000002", sameSecond, ExecutionDirection.UP));

        assertThat(repository.wasExecuted(NodeId.of("node1"), EnvironmentId.of("testdb"))).isTrue();
    }

    @Test
    void tiedTimestampsAreBrokenByIdWhenInsertedInReverseIdOrder() {
        repository.initialize();
        Instant sameSecond = Instant.parse("2026-08-20T10:00:00Z");

        // Inserted newest-first: physical order contradicts id order, so only id ordering wins.
        repository.record(
                recordAt(
                        "00000000-0000-7000-8000-000000000002",
                        sameSecond,
                        ExecutionDirection.DOWN));
        repository.record(
                recordAt(
                        "00000000-0000-7000-8000-000000000001", sameSecond, ExecutionDirection.UP));

        assertThat(repository.wasExecuted(NodeId.of("node1"), EnvironmentId.of("testdb")))
                .isFalse();
    }

    @Test
    void findLatestRecordBreaksTiedTimestampsById() {
        repository.initialize();
        Instant sameSecond = Instant.parse("2026-08-20T10:00:00Z");

        repository.record(
                recordAt(
                        "00000000-0000-7000-8000-000000000002",
                        sameSecond,
                        ExecutionDirection.DOWN));
        repository.record(
                recordAt(
                        "00000000-0000-7000-8000-000000000001", sameSecond, ExecutionDirection.UP));

        var latest = repository.findLatestRecord(NodeId.of("node1"), EnvironmentId.of("testdb"));
        assertThat(latest).isNotNull();
        assertThat(latest.id()).isEqualTo("00000000-0000-7000-8000-000000000002");
    }

    @Test
    void executedNodesBreaksTiedTimestampsById() {
        repository.initialize();
        Instant sameSecond = Instant.parse("2026-08-20T10:00:00Z");

        repository.record(
                recordAt(
                        "00000000-0000-7000-8000-000000000001", sameSecond, ExecutionDirection.UP));
        repository.record(
                recordAt(
                        "00000000-0000-7000-8000-000000000002",
                        sameSecond,
                        ExecutionDirection.DOWN));

        assertThat(repository.executedNodes(EnvironmentId.of("testdb"))).isEmpty();
    }

    @Test
    void fingerprintRoundTripsThroughTheHistoryTable() {
        repository.initialize();

        repository.record(
                new ExecutionRecord(
                        "00000000-0000-7000-8000-0000000000fa",
                        NodeId.of("node1"),
                        EnvironmentId.of("testdb"),
                        ExecutionDirection.UP,
                        ExecutionStatus.SUCCESS,
                        Instant.now(),
                        "test description",
                        "DOWN SQL",
                        100L,
                        null,
                        "5ea918fac5561634f4b577815b41483e5882b9c57dd3bd2351e3422d641af545"));
        repository.record(
                new ExecutionRecord(
                        "00000000-0000-7000-8000-0000000000fb",
                        NodeId.of("node2"),
                        EnvironmentId.of("testdb"),
                        ExecutionDirection.UP,
                        ExecutionStatus.SUCCESS,
                        Instant.now(),
                        "test description",
                        "DOWN SQL",
                        100L,
                        null,
                        null));

        var withFingerprint =
                repository.findLatestRecord(NodeId.of("node1"), EnvironmentId.of("testdb"));
        assertThat(withFingerprint).isNotNull();
        assertThat(withFingerprint.fingerprint())
                .isEqualTo("5ea918fac5561634f4b577815b41483e5882b9c57dd3bd2351e3422d641af545");

        var withoutFingerprint =
                repository.findLatestRecord(NodeId.of("node2"), EnvironmentId.of("testdb"));
        assertThat(withoutFingerprint).isNotNull();
        assertThat(withoutFingerprint.fingerprint()).isNull();
    }

    @Test
    void updateFingerprintReplacesOnlyTheFingerprint() {
        repository.initialize();

        repository.record(
                new ExecutionRecord(
                        "00000000-0000-7000-8000-0000000000fc",
                        NodeId.of("node1"),
                        EnvironmentId.of("testdb"),
                        ExecutionDirection.UP,
                        ExecutionStatus.SUCCESS,
                        Instant.parse("2026-01-30T12:34:56Z"),
                        "test description",
                        "DOWN SQL",
                        100L,
                        null,
                        null));

        var before = repository.findLatestRecord(NodeId.of("node1"), EnvironmentId.of("testdb"));
        assertThat(before).isNotNull();
        assertThat(before.fingerprint()).isNull();

        boolean updated =
                repository.updateFingerprint("00000000-0000-7000-8000-0000000000fc", "abc");

        assertThat(updated).isTrue();

        var after = repository.findLatestRecord(NodeId.of("node1"), EnvironmentId.of("testdb"));
        assertThat(after).isNotNull();
        assertThat(after.fingerprint()).isEqualTo("abc");
        assertThat(after.executedAt()).isEqualTo(before.executedAt());
        assertThat(after.durationMs()).isEqualTo(before.durationMs());
        assertThat(after.serializedDownTask()).isEqualTo(before.serializedDownTask());
    }

    private ExecutionRecord recordAt(String id, Instant executedAt, ExecutionDirection direction) {
        return new ExecutionRecord(
                id,
                NodeId.of("node1"),
                EnvironmentId.of("testdb"),
                direction,
                ExecutionStatus.SUCCESS,
                executedAt,
                "test description",
                direction == ExecutionDirection.UP ? "DOWN SQL" : null,
                100L,
                null,
                null);
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
                status == ExecutionStatus.FAILURE ? "test error" : null,
                null);
    }
}
