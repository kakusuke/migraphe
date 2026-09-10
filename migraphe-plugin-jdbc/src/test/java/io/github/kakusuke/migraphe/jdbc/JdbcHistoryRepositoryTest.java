package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionOrigin;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.ExecutionStatus;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcHistoryRepositoryTest {

    private JdbcTarget target;
    private JdbcHistoryRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        target =
                JdbcTarget.create(
                        "testdb",
                        "jdbc:h2:mem:history_test;DB_CLOSE_DELAY=-1",
                        "sa",
                        "",
                        "org.h2.Driver",
                        "H2");
        // Clean up
        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS migraphe_history");
        }
        repository = new JdbcHistoryRepository(target);
    }

    @Test
    void initializeCreatesTable() throws Exception {
        repository.initialize();

        try (Connection conn = target.createConnection();
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
    void reportsWhetherTheHistoryHasBeenCreatedYet() {
        assertThat(repository.isInitialized()).isFalse();

        repository.initialize();

        assertThat(repository.isInitialized()).isTrue();
    }

    @Test
    void initializeCreatesEveryColumnThisVersionWritesAndLeavesAnOlderTableAlone()
            throws Exception {
        repository.initialize();

        assertThat(columnsOfHistoryTable())
                .contains(
                        "ID",
                        "NODE_ID",
                        "TARGET_ID",
                        "DIRECTION",
                        "STATUS",
                        "EXECUTED_AT",
                        "DESCRIPTION",
                        "SERIALIZED_DOWN_TASK",
                        "DURATION_MS",
                        "ERROR_MESSAGE",
                        "FINGERPRINT",
                        "PLUGIN_METADATA",
                        "DEPENDENCIES",
                        "ORIGIN",
                        "NO_WAY_BACK");

        createPreUpgradeHistoryTable();

        repository.initialize();

        assertThat(columnsOfHistoryTable())
                .contains("ENVIRONMENT_ID")
                .doesNotContain("TARGET_ID", "FINGERPRINT", "ORIGIN");
    }

    @Test
    void recordAndRetrieve() {
        repository.initialize();

        var record =
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS);
        repository.record(record);

        var latest = repository.findLatestRecord(NodeId.of("node1"));
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

        assertThat(repository.wasExecuted(NodeId.of("node1"))).isTrue();
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

        assertThat(repository.wasExecuted(NodeId.of("node1"))).isFalse();
    }

    @Test
    void wasExecutedReturnsTrueWhenTheRollbackFailed() {
        repository.initialize();

        repository.record(
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS));
        repository.record(
                createRecord(
                        "rec2",
                        "node1",
                        "testdb",
                        ExecutionDirection.DOWN,
                        ExecutionStatus.FAILURE));

        assertThat(repository.wasExecuted(NodeId.of("node1"))).isTrue();
    }

    @Test
    void executedNodesIncludesANodeWhoseRollbackFailed() {
        repository.initialize();

        repository.record(
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS));
        repository.record(
                createRecord(
                        "rec2",
                        "node1",
                        "testdb",
                        ExecutionDirection.DOWN,
                        ExecutionStatus.FAILURE));

        assertThat(repository.executedNodes()).containsExactly(NodeId.of("node1"));
    }

    @Test
    void wasExecutedStaysTrueWhenAReapplyFailed() {
        repository.initialize();

        repository.record(
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS));
        repository.record(
                createRecord(
                        "rec2", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.FAILURE));

        assertThat(repository.wasExecuted(NodeId.of("node1"))).isTrue();
    }

    @Test
    void wasExecutedStaysFalseWhenAReRollbackFailedAfterASuccessfulOne() {
        repository.initialize();

        repository.record(
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS));
        repository.record(
                createRecord(
                        "rec2",
                        "node1",
                        "testdb",
                        ExecutionDirection.DOWN,
                        ExecutionStatus.SUCCESS));
        repository.record(
                createRecord(
                        "rec3",
                        "node1",
                        "testdb",
                        ExecutionDirection.DOWN,
                        ExecutionStatus.FAILURE));

        assertThat(repository.wasExecuted(NodeId.of("node1"))).isFalse();
    }

    @Test
    void wasExecutedReturnsFalseForFailure() {
        repository.initialize();

        var record =
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.FAILURE);
        repository.record(record);

        assertThat(repository.wasExecuted(NodeId.of("node1"))).isFalse();
    }

    @Test
    void wasExecutedReturnsFalseForUnknownNode() {
        repository.initialize();
        assertThat(repository.wasExecuted(NodeId.of("unknown"))).isFalse();
    }

    @Test
    void aRollbackInAnotherTargetTakesTheIdentifierOutOfTheAppliedSet() {
        repository.initialize();

        repository.record(
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS));
        repository.record(
                createRecord(
                        "rec2",
                        "node1",
                        "other",
                        ExecutionDirection.DOWN,
                        ExecutionStatus.SUCCESS));

        assertThat(repository.wasExecuted(NodeId.of("node1"))).isFalse();
        assertThat(repository.executedNodes()).isEmpty();
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

        List<NodeId> nodes = repository.executedNodes();
        assertThat(nodes).containsExactly(NodeId.of("node1"), NodeId.of("node2"));
    }

    @Test
    void findLatestRecordReturnsNullForUnknown() {
        repository.initialize();
        assertThat(repository.findLatestRecord(NodeId.of("unknown"))).isNull();
    }

    @Test
    void findLatestRecordCrossesTargetsBecauseTheIdentifierIsUniqueAcrossTheProject() {
        repository.initialize();

        repository.record(
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS));
        repository.record(
                createRecord(
                        "rec2",
                        "node1",
                        "other",
                        ExecutionDirection.DOWN,
                        ExecutionStatus.SUCCESS));

        var latest = repository.findLatestRecord(NodeId.of("node1"));
        assertThat(latest).isNotNull();
        assertThat(latest.id()).isEqualTo("rec2");
        assertThat(latest.targetId()).isEqualTo(TargetId.of("other"));
        assertThat(latest.direction()).isEqualTo(ExecutionDirection.DOWN);
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

        List<ExecutionRecord> records = repository.allRecords();
        assertThat(records).hasSize(2);
        assertThat(records.get(0).id()).isEqualTo("rec1");
        assertThat(records.get(1).id()).isEqualTo("rec2");
    }

    @Test
    void allRecordsReturnsEveryTargetsRows() {
        repository.initialize();

        repository.record(
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS));
        repository.record(
                createRecord(
                        "rec2", "node2", "other", ExecutionDirection.UP, ExecutionStatus.SUCCESS));

        assertThat(repository.allRecords())
                .extracting(record -> record.targetId().value())
                .containsExactlyInAnyOrder("testdb", "other");
    }

    @Test
    void customSchemaResourcePath() {
        var customRepo =
                new JdbcHistoryRepository(
                        target, "/io/github/kakusuke/migraphe/jdbc/schema/init_history_table.sql");
        customRepo.initialize();
        customRepo.record(
                createRecord(
                        "rec1", "node1", "testdb", ExecutionDirection.UP, ExecutionStatus.SUCCESS));
        assertThat(customRepo.wasExecuted(NodeId.of("node1"))).isTrue();
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

        assertThat(repository.wasExecuted(NodeId.of("node1"))).isFalse();
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

        assertThat(repository.wasExecuted(NodeId.of("node1"))).isTrue();
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

        assertThat(repository.wasExecuted(NodeId.of("node1"))).isFalse();
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

        var latest = repository.findLatestRecord(NodeId.of("node1"));
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

        assertThat(repository.executedNodes()).isEmpty();
    }

    @Test
    void theDependenciesRoundTripAndKeepEmptyApartFromUnrecorded() {
        repository.initialize();

        record(
                "00000000-0000-7000-8000-000000000101",
                "stood_on_two",
                List.of(NodeId.of("db1/001_a"), NodeId.of("db1/002_b")));
        record("00000000-0000-7000-8000-000000000102", "stood_on_nothing", List.of());
        record("00000000-0000-7000-8000-000000000103", "unrecorded", null);

        assertThat(latest("stood_on_two").dependencies())
                .containsExactly(NodeId.of("db1/001_a"), NodeId.of("db1/002_b"));
        assertThat(latest("stood_on_nothing").dependencies()).isEmpty();
        assertThat(latest("unrecorded").dependencies()).isNull();
    }

    private void record(String id, String nodeId, @Nullable List<NodeId> dependencies) {
        repository.record(
                new ExecutionRecord(
                        id,
                        NodeId.of(nodeId),
                        TargetId.of("testdb"),
                        ExecutionDirection.UP,
                        ExecutionStatus.SUCCESS,
                        Instant.now(),
                        "test description",
                        "DOWN SQL",
                        100L,
                        null,
                        null,
                        null,
                        dependencies,
                        ExecutionOrigin.EXECUTED,
                        null));
    }

    private ExecutionRecord latest(String nodeId) {
        ExecutionRecord found = repository.findLatestRecord(NodeId.of(nodeId));
        assertThat(found).isNotNull();
        return found;
    }

    @Test
    void pluginMetadataRoundTripsThroughTheHistoryTable() {
        repository.initialize();

        repository.record(
                new ExecutionRecord(
                        "00000000-0000-7000-8000-0000000000fc",
                        NodeId.of("node1"),
                        TargetId.of("testdb"),
                        ExecutionDirection.UP,
                        ExecutionStatus.SUCCESS,
                        Instant.now(),
                        "test description",
                        "DOWN SQL",
                        100L,
                        null,
                        null,
                        "autocommit.down=true\n",
                        null,
                        ExecutionOrigin.EXECUTED,
                        null));
        repository.record(
                new ExecutionRecord(
                        "00000000-0000-7000-8000-0000000000fd",
                        NodeId.of("node2"),
                        TargetId.of("testdb"),
                        ExecutionDirection.UP,
                        ExecutionStatus.SUCCESS,
                        Instant.now(),
                        "test description",
                        "DOWN SQL",
                        100L,
                        null,
                        null,
                        null,
                        null,
                        ExecutionOrigin.EXECUTED,
                        null));

        var withMetadata = repository.findLatestRecord(NodeId.of("node1"));
        assertThat(withMetadata).isNotNull();
        assertThat(withMetadata.pluginMetadata()).isEqualTo("autocommit.down=true\n");

        var withoutMetadata = repository.findLatestRecord(NodeId.of("node2"));
        assertThat(withoutMetadata).isNotNull();
        assertThat(withoutMetadata.pluginMetadata()).isNull();
    }

    @Test
    void fingerprintRoundTripsThroughTheHistoryTable() {
        repository.initialize();

        repository.record(
                new ExecutionRecord(
                        "00000000-0000-7000-8000-0000000000fa",
                        NodeId.of("node1"),
                        TargetId.of("testdb"),
                        ExecutionDirection.UP,
                        ExecutionStatus.SUCCESS,
                        Instant.now(),
                        "test description",
                        "DOWN SQL",
                        100L,
                        null,
                        "5ea918fac5561634f4b577815b41483e5882b9c57dd3bd2351e3422d641af545",
                        null,
                        null,
                        ExecutionOrigin.EXECUTED,
                        null));
        repository.record(
                new ExecutionRecord(
                        "00000000-0000-7000-8000-0000000000fb",
                        NodeId.of("node2"),
                        TargetId.of("testdb"),
                        ExecutionDirection.UP,
                        ExecutionStatus.SUCCESS,
                        Instant.now(),
                        "test description",
                        "DOWN SQL",
                        100L,
                        null,
                        null,
                        null,
                        null,
                        ExecutionOrigin.EXECUTED,
                        null));

        var withFingerprint = repository.findLatestRecord(NodeId.of("node1"));
        assertThat(withFingerprint).isNotNull();
        assertThat(withFingerprint.fingerprint())
                .isEqualTo("5ea918fac5561634f4b577815b41483e5882b9c57dd3bd2351e3422d641af545");

        var withoutFingerprint = repository.findLatestRecord(NodeId.of("node2"));
        assertThat(withoutFingerprint).isNotNull();
        assertThat(withoutFingerprint.fingerprint()).isNull();
    }

    private ExecutionRecord recordAt(String id, Instant executedAt, ExecutionDirection direction) {
        return new ExecutionRecord(
                id,
                NodeId.of("node1"),
                TargetId.of("testdb"),
                direction,
                ExecutionStatus.SUCCESS,
                executedAt,
                "test description",
                direction == ExecutionDirection.UP ? "DOWN SQL" : null,
                100L,
                null,
                null,
                null,
                null,
                ExecutionOrigin.EXECUTED,
                null);
    }

    private ExecutionRecord createRecord(
            String id,
            String nodeId,
            String targetId,
            ExecutionDirection direction,
            ExecutionStatus status) {
        return new ExecutionRecord(
                id,
                NodeId.of(nodeId),
                TargetId.of(targetId),
                direction,
                status,
                Instant.now(),
                "test description",
                direction == ExecutionDirection.UP ? "DOWN SQL" : null,
                100L,
                status == ExecutionStatus.FAILURE ? "test error" : null,
                null,
                null,
                null,
                ExecutionOrigin.EXECUTED,
                null);
    }

    @Test
    void recordsWhetherARowWasExecutedOrClaimed() {
        repository.initialize();
        repository.record(
                new ExecutionRecord(
                        "00000000-0000-7000-8000-0000000000fc",
                        NodeId.of("claimed"),
                        TargetId.of("testdb"),
                        ExecutionDirection.UP,
                        ExecutionStatus.SUCCESS,
                        Instant.now(),
                        "test description",
                        "DOWN SQL",
                        0L,
                        null,
                        "a token",
                        null,
                        null,
                        ExecutionOrigin.AMENDED,
                        null));

        var claimed = repository.findLatestRecord(NodeId.of("claimed"));
        assertThat(claimed).isNotNull();
        assertThat(claimed.origin()).isEqualTo(ExecutionOrigin.AMENDED);

        assertThat(
                        ExecutionRecord.upSuccess(
                                        NodeId.of("ran"),
                                        TargetId.of("testdb"),
                                        "test description",
                                        null,
                                        0L)
                                .origin())
                .isEqualTo(ExecutionOrigin.EXECUTED);
    }

    @Test
    void aRowFromBeforeTheOriginColumnReadsAsExecuted() throws Exception {
        repository.initialize();
        repository.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("node1"), TargetId.of("testdb"), "test description", null, 0L));
        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE migraphe_history SET origin = NULL");
        }

        var legacy = repository.findLatestRecord(NodeId.of("node1"));

        assertThat(legacy).isNotNull();
        assertThat(legacy.origin()).isEqualTo(ExecutionOrigin.EXECUTED);
    }

    @Test
    void theOneWayReasonRoundTripsThroughTheHistoryTable() {
        repository.initialize();
        repository.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("oneWay"),
                        TargetId.of("testdb"),
                        "test description",
                        null,
                        0L,
                        "a token",
                        null,
                        List.of(),
                        "DROP COLUMN discards the data"));
        repository.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("reversible"),
                        TargetId.of("testdb"),
                        "test description",
                        "DOWN SQL",
                        0L,
                        "a token",
                        null,
                        List.of()));

        var oneWay = repository.findLatestRecord(NodeId.of("oneWay"));
        assertThat(oneWay).isNotNull();
        assertThat(oneWay.noWayBack()).isEqualTo("DROP COLUMN discards the data");

        var reversible = repository.findLatestRecord(NodeId.of("reversible"));
        assertThat(reversible).isNotNull();
        assertThat(reversible.noWayBack()).isNull();
    }

    private List<String> columnsOfHistoryTable() throws Exception {
        List<String> columns = new java.util.ArrayList<>();
        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement();
                var rs =
                        stmt.executeQuery(
                                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS"
                                        + " WHERE TABLE_NAME = 'MIGRAPHE_HISTORY'")) {
            while (rs.next()) {
                columns.add(rs.getString(1).toUpperCase(java.util.Locale.ROOT));
            }
        }
        return columns;
    }

    private void createPreUpgradeHistoryTable() throws Exception {
        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS migraphe_history");
            stmt.execute(
                    "CREATE TABLE migraphe_history ("
                            + " id VARCHAR(64) PRIMARY KEY,"
                            + " node_id VARCHAR(255) NOT NULL,"
                            + " environment_id VARCHAR(255) NOT NULL,"
                            + " direction VARCHAR(10) NOT NULL,"
                            + " status VARCHAR(10) NOT NULL,"
                            + " executed_at TIMESTAMP NOT NULL,"
                            + " description TEXT,"
                            + " serialized_down_task TEXT,"
                            + " duration_ms BIGINT,"
                            + " error_message TEXT)");
        }
    }
}
