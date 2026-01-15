package io.github.migraphe.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.migraphe.api.common.Result;
import io.github.migraphe.api.graph.NodeId;
import io.github.migraphe.api.history.ExecutionRecord;
import io.github.migraphe.api.history.ExecutionStatus;
import io.github.migraphe.api.history.HistoryRepository;
import io.github.migraphe.api.task.ExecutionDirection;
import io.github.migraphe.api.task.Task;
import io.github.migraphe.api.task.TaskResult;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PostgreSQLIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("migraphe_test")
                    .withUsername("test")
                    .withPassword("test")
                    .waitingFor(new HostPortWaitStrategy().forPorts(5432));

    private PostgreSQLEnvironment environment;
    private HistoryRepository historyRepo;

    @BeforeEach
    void setUp() {
        environment =
                PostgreSQLEnvironment.create(
                        "test",
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword());

        historyRepo = new PostgreSQLHistoryRepository(environment);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up database after each test
        try (Connection conn = environment.createConnection();
                Statement stmt = conn.createStatement()) {
            // Drop all user tables
            stmt.execute("DROP TABLE IF EXISTS posts CASCADE");
            stmt.execute("DROP TABLE IF EXISTS users CASCADE");
            stmt.execute("DROP TABLE IF EXISTS autocommit_test CASCADE");
            stmt.execute("DROP TABLE IF EXISTS autocommit_down_test CASCADE");
            // Clear history
            stmt.execute("TRUNCATE TABLE migraphe_history");
        }
    }

    @Test
    void shouldInitializeHistorySchema() throws Exception {
        // when
        historyRepo.initialize();

        // then - テーブルが存在することを確認
        try (Connection conn = environment.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT table_name FROM information_schema.tables "
                                        + "WHERE table_name = 'migraphe_history'")) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    void shouldExecuteUpMigrationAndPersistHistory() throws Exception {
        // given
        historyRepo.initialize();

        PostgreSQLMigrationNode node =
                PostgreSQLMigrationNode.builder()
                        .id("V001")
                        .name("Create users table")
                        .environment(environment)
                        .upSql("CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100));")
                        .downSql("DROP TABLE IF EXISTS users;")
                        .build();

        // when
        Task upTask = node.upTask();
        Result<TaskResult, String> result = upTask.execute();

        // then
        assertThat(result.isOk()).isTrue();
        TaskResult taskResult = result.value();
        assertThat(taskResult).isNotNull();
        assertThat(taskResult.serializedDownTask()).isNotNull();

        // Verify table exists
        try (Connection conn = environment.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT table_name FROM information_schema.tables "
                                        + "WHERE table_name = 'users'")) {
            assertThat(rs.next()).isTrue();
        }

        // Record execution in history
        ExecutionRecord record =
                ExecutionRecord.upSuccess(
                        node.id(),
                        environment.id(),
                        "Create users table",
                        taskResult.serializedDownTask(),
                        100);
        historyRepo.record(record);

        // Verify history persisted
        assertThat(historyRepo.wasExecuted(node.id(), environment.id())).isTrue();
        assertThat(historyRepo.executedNodes(environment.id())).containsExactly(node.id());
    }

    @Test
    void shouldExecuteDownMigration() throws Exception {
        // given
        historyRepo.initialize();

        // First create a table
        PostgreSQLMigrationNode node =
                PostgreSQLMigrationNode.builder()
                        .id("V001")
                        .name("Create users table")
                        .environment(environment)
                        .upSql("CREATE TABLE users (id SERIAL PRIMARY KEY);")
                        .downSql("DROP TABLE IF EXISTS users;")
                        .build();

        node.upTask().execute();

        // when - Execute down migration
        Task downTask = Objects.requireNonNull(node.downTask());
        Result<TaskResult, String> result = downTask.execute();

        // then
        assertThat(result.isOk()).isTrue();
        assertThat(result.value()).isNotNull();
        assertThat(result.value().serializedDownTask()).isNull();

        // Verify table does not exist
        try (Connection conn = environment.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT table_name FROM information_schema.tables "
                                        + "WHERE table_name = 'users'")) {
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    void shouldRollbackOnSqlError() {
        // given
        PostgreSQLMigrationNode node =
                PostgreSQLMigrationNode.builder()
                        .id("V002")
                        .name("Invalid SQL")
                        .environment(environment)
                        .upSql("INVALID SQL SYNTAX;")
                        .build();

        // when
        Task upTask = node.upTask();
        Result<TaskResult, String> result = upTask.execute();

        // then
        assertThat(result.isErr()).isTrue();
        assertThat(result.error()).isNotNull();
        assertThat(result.error()).contains("Failed to execute UP migration");
    }

    @Test
    void shouldHandleMultipleMigrations() throws Exception {
        // given
        historyRepo.initialize();

        PostgreSQLMigrationNode node1 =
                PostgreSQLMigrationNode.builder()
                        .id("V001")
                        .name("Create users table")
                        .environment(environment)
                        .upSql("CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100));")
                        .downSql("DROP TABLE IF EXISTS users;")
                        .build();

        PostgreSQLMigrationNode node2 =
                PostgreSQLMigrationNode.builder()
                        .id("V002")
                        .name("Create posts table")
                        .environment(environment)
                        .dependencies(NodeId.of("V001"))
                        .upSql(
                                "CREATE TABLE posts (id SERIAL PRIMARY KEY, user_id INT REFERENCES"
                                        + " users(id));")
                        .downSql("DROP TABLE IF EXISTS posts;")
                        .build();

        // when - Execute both migrations
        Result<TaskResult, String> result1 = node1.upTask().execute();
        Result<TaskResult, String> result2 = node2.upTask().execute();

        // then
        assertThat(result1.isOk()).isTrue();
        assertThat(result2.isOk()).isTrue();

        // Record both in history
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        node1.id(),
                        environment.id(),
                        node1.name(),
                        result1.value().serializedDownTask(),
                        100));

        historyRepo.record(
                ExecutionRecord.upSuccess(
                        node2.id(),
                        environment.id(),
                        node2.name(),
                        result2.value().serializedDownTask(),
                        150));

        // Verify both executed
        assertThat(historyRepo.executedNodes(environment.id()))
                .containsExactlyInAnyOrder(node1.id(), node2.id());
    }

    @Test
    void shouldRetrieveLatestRecord() {
        // given
        historyRepo.initialize();

        NodeId nodeId = NodeId.of("V001");
        ExecutionRecord record1 =
                ExecutionRecord.upSuccess(nodeId, environment.id(), "First execution", null, 100);
        ExecutionRecord record2 =
                ExecutionRecord.downSuccess(nodeId, environment.id(), "Rollback", 50);

        // when
        historyRepo.record(record1);
        historyRepo.record(record2);

        // then
        var latest = historyRepo.findLatestRecord(nodeId, environment.id());
        assertThat(latest).isNotNull();
        assertThat(latest.id()).isEqualTo(record2.id());
    }

    @Test
    void shouldNotConsiderFailedExecutionAsExecuted() {
        // given
        historyRepo.initialize();

        NodeId nodeId = NodeId.of("V001");
        ExecutionRecord failedRecord =
                ExecutionRecord.failure(
                        nodeId,
                        environment.id(),
                        ExecutionDirection.UP,
                        "Failed migration",
                        "SQL syntax error");

        // when
        historyRepo.record(failedRecord);

        // then
        assertThat(historyRepo.wasExecuted(nodeId, environment.id())).isFalse();
    }

    @Test
    void shouldGetAllRecordsForEnvironment() {
        // given
        historyRepo.initialize();

        NodeId node1 = NodeId.of("V001");
        NodeId node2 = NodeId.of("V002");
        ExecutionRecord record1 =
                ExecutionRecord.upSuccess(node1, environment.id(), "Migration 1", null, 100);
        ExecutionRecord record2 =
                ExecutionRecord.upSuccess(node2, environment.id(), "Migration 2", null, 150);

        // when
        historyRepo.record(record1);
        historyRepo.record(record2);

        // then
        var allRecords = historyRepo.allRecords(environment.id());
        assertThat(allRecords).hasSize(2);
        assertThat(allRecords.get(0).status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(allRecords.get(1).status()).isEqualTo(ExecutionStatus.SUCCESS);
    }

    @Test
    void shouldExecuteUpMigrationWithAutocommit() throws Exception {
        // given
        historyRepo.initialize();

        PostgreSQLMigrationNode node =
                PostgreSQLMigrationNode.builder()
                        .id("autocommit_test")
                        .name("Autocommit migration")
                        .environment(environment)
                        .upSql("CREATE TABLE autocommit_test (id SERIAL PRIMARY KEY);")
                        .downSql("DROP TABLE IF EXISTS autocommit_test;")
                        .autocommit(true)
                        .build();

        // when
        Task upTask = node.upTask();
        Result<TaskResult, String> result = upTask.execute();

        // then
        assertThat(result.isOk()).isTrue();
        assertThat(result.value().message()).contains("autocommit");

        // Verify table exists
        try (Connection conn = environment.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT table_name FROM information_schema.tables "
                                        + "WHERE table_name = 'autocommit_test'")) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    void shouldExecuteDownMigrationWithAutocommit() throws Exception {
        // given
        historyRepo.initialize();

        PostgreSQLMigrationNode node =
                PostgreSQLMigrationNode.builder()
                        .id("autocommit_down")
                        .name("Autocommit down migration")
                        .environment(environment)
                        .upSql("CREATE TABLE autocommit_down_test (id SERIAL);")
                        .downSql("DROP TABLE IF EXISTS autocommit_down_test;")
                        .autocommit(true)
                        .build();

        node.upTask().execute();

        // when
        Task downTask = Objects.requireNonNull(node.downTask());
        Result<TaskResult, String> result = downTask.execute();

        // then
        assertThat(result.isOk()).isTrue();
        assertThat(result.value().message()).contains("autocommit");

        // Verify table does not exist
        try (Connection conn = environment.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT table_name FROM information_schema.tables "
                                        + "WHERE table_name = 'autocommit_down_test'")) {
            assertThat(rs.next()).isFalse();
        }
    }
}
