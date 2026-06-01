package io.github.kakusuke.migraphe.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.ExecutionStatus;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import io.github.kakusuke.migraphe.jdbc.JdbcHistoryRepository;
import io.github.kakusuke.migraphe.jdbc.JdbcMigrationNode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MySQLIntegrationTest {

    @Container
    static MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0").withDatabaseName("migraphe_test");

    private static final String MYSQL_SCHEMA_RESOURCE =
            "/io/github/kakusuke/migraphe/mysql/schema/init_history_table.sql";

    private MySQLEnvironment environment;
    private HistoryRepository historyRepo;

    @BeforeEach
    void setUp() {
        environment =
                MySQLEnvironment.create(
                        "test", mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());

        historyRepo = new JdbcHistoryRepository(environment, MYSQL_SCHEMA_RESOURCE);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up database after each test
        try (Connection conn = environment.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS posts");
            stmt.execute("DROP TABLE IF EXISTS users");
            stmt.execute("DROP TABLE IF EXISTS autocommit_test");
            stmt.execute("DROP TABLE IF EXISTS autocommit_down_test");
            stmt.execute("DROP TABLE IF EXISTS node_test");
            stmt.execute("DROP TABLE IF EXISTS rollback_test");
            stmt.execute("DROP TABLE IF EXISTS multi_a");
            stmt.execute("DROP TABLE IF EXISTS multi_b");
            stmt.execute("DROP TABLE IF EXISTS proc_target");
            stmt.execute("DROP PROCEDURE IF EXISTS seed_proc");
            stmt.execute("DROP PROCEDURE IF EXISTS delim_proc");
            stmt.execute("DROP TABLE IF EXISTS delim_target");
            stmt.execute("TRUNCATE TABLE migraphe_history");
        }
    }

    @Test
    void shouldExecuteMultipleCreateTableInTransactionMode() throws Exception {
        // given - autocommit=false (default / transaction mode), multiple statements in one task.
        // This case previously failed because the whole text was sent as a single statement.
        JdbcMigrationNode node =
                JdbcMigrationNode.builder()
                        .id("multi_ddl")
                        .name("Create two tables")
                        .environment(environment)
                        .upSql(
                                "CREATE TABLE multi_a (id INT PRIMARY KEY);\n"
                                        + "CREATE TABLE multi_b (id INT PRIMARY KEY);\n")
                        .downSql("DROP TABLE IF EXISTS multi_b;\nDROP TABLE IF EXISTS multi_a;\n")
                        .build();

        // when
        Result<TaskResult, String> result = node.upTask().execute();

        // then
        assertThat(result.isOk()).isTrue();
        assertThat(tableExists("multi_a")).isTrue();
        assertThat(tableExists("multi_b")).isTrue();
    }

    @Test
    void shouldExecuteProcedureWithoutDelimiterInTransactionMode() throws Exception {
        // given - CREATE PROCEDURE with inline BEGIN...END (no DELIMITER directive).
        JdbcMigrationNode node =
                JdbcMigrationNode.builder()
                        .id("proc_inline")
                        .name("Create procedure inline")
                        .environment(environment)
                        .upSql(
                                "CREATE TABLE proc_target (id INT);\n"
                                        + "CREATE PROCEDURE seed_proc()\n"
                                        + "BEGIN\n"
                                        + "  INSERT INTO proc_target VALUES (1);\n"
                                        + "  INSERT INTO proc_target VALUES (2);\n"
                                        + "END")
                        .downSql(
                                "DROP PROCEDURE IF EXISTS seed_proc;\n"
                                        + "DROP TABLE IF EXISTS proc_target;\n")
                        .build();

        // when
        Result<TaskResult, String> result = node.upTask().execute();

        // then
        assertThat(result.isOk()).isTrue();
        assertThat(procedureExists("seed_proc")).isTrue();
    }

    @Test
    void shouldExecuteDelimiterScriptInTransactionMode() throws Exception {
        // given - DELIMITER $$ ... END$$ DELIMITER ; style script.
        JdbcMigrationNode node =
                JdbcMigrationNode.builder()
                        .id("proc_delim")
                        .name("Create procedure with DELIMITER")
                        .environment(environment)
                        .upSql(
                                "CREATE TABLE delim_target (id INT);\n"
                                        + "DELIMITER $$\n"
                                        + "CREATE PROCEDURE delim_proc()\n"
                                        + "BEGIN\n"
                                        + "  INSERT INTO delim_target VALUES (1);\n"
                                        + "  INSERT INTO delim_target VALUES (2);\n"
                                        + "END$$\n"
                                        + "DELIMITER ;\n")
                        .downSql(
                                "DROP PROCEDURE IF EXISTS delim_proc;\n"
                                        + "DROP TABLE IF EXISTS delim_target;\n")
                        .build();

        // when
        Result<TaskResult, String> result = node.upTask().execute();

        // then
        assertThat(result.isOk()).isTrue();
        assertThat(procedureExists("delim_proc")).isTrue();
    }

    private boolean tableExists(String table) throws Exception {
        try (Connection conn = environment.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT table_name FROM information_schema.tables "
                                        + "WHERE table_name = '"
                                        + table
                                        + "' AND table_schema = 'migraphe_test'")) {
            return rs.next();
        }
    }

    private boolean procedureExists(String name) throws Exception {
        try (Connection conn = environment.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT routine_name FROM information_schema.routines "
                                        + "WHERE routine_name = '"
                                        + name
                                        + "' AND routine_schema = 'migraphe_test'")) {
            return rs.next();
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
                                        + "WHERE table_name = 'migraphe_history' "
                                        + "AND table_schema = 'migraphe_test'")) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    void shouldExecuteUpMigrationAndPersistHistory() throws Exception {
        // given
        historyRepo.initialize();

        JdbcMigrationNode node =
                JdbcMigrationNode.builder()
                        .id("V001")
                        .name("Create users table")
                        .environment(environment)
                        .upSql(
                                "CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY, name"
                                        + " VARCHAR(100));")
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
                                        + "WHERE table_name = 'users' "
                                        + "AND table_schema = 'migraphe_test'")) {
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

        JdbcMigrationNode node =
                JdbcMigrationNode.builder()
                        .id("V001")
                        .name("Create users table")
                        .environment(environment)
                        .upSql("CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY);")
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
                                        + "WHERE table_name = 'users' "
                                        + "AND table_schema = 'migraphe_test'")) {
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    void shouldRollbackOnSqlError() {
        // given
        JdbcMigrationNode node =
                JdbcMigrationNode.builder()
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

        JdbcMigrationNode node1 =
                JdbcMigrationNode.builder()
                        .id("V001")
                        .name("Create users table")
                        .environment(environment)
                        .upSql(
                                "CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY, name"
                                        + " VARCHAR(100));")
                        .downSql("DROP TABLE IF EXISTS users;")
                        .build();

        JdbcMigrationNode node2 =
                JdbcMigrationNode.builder()
                        .id("V002")
                        .name("Create posts table")
                        .environment(environment)
                        .dependencies(NodeId.of("V001"))
                        .upSql(
                                "CREATE TABLE posts (id INT AUTO_INCREMENT PRIMARY KEY, user_id"
                                        + " INT, FOREIGN KEY (user_id) REFERENCES users(id));")
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

        JdbcMigrationNode node =
                JdbcMigrationNode.builder()
                        .id("autocommit_test")
                        .name("Autocommit migration")
                        .environment(environment)
                        .upSql("CREATE TABLE autocommit_test (id INT AUTO_INCREMENT PRIMARY KEY);")
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
                                        + "WHERE table_name = 'autocommit_test' "
                                        + "AND table_schema = 'migraphe_test'")) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    void shouldExecuteDownMigrationWithAutocommit() throws Exception {
        // given
        historyRepo.initialize();

        JdbcMigrationNode node =
                JdbcMigrationNode.builder()
                        .id("autocommit_down")
                        .name("Autocommit down migration")
                        .environment(environment)
                        .upSql("CREATE TABLE autocommit_down_test (id INT);")
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
                                        + "WHERE table_name = 'autocommit_down_test' "
                                        + "AND table_schema = 'migraphe_test'")) {
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    void shouldCreateCorrectTasksFromMigrationNode() throws Exception {
        // given
        historyRepo.initialize();

        var node =
                JdbcMigrationNode.builder()
                        .id("mysql_node")
                        .name("MySQL test node")
                        .environment(environment)
                        .upSql(
                                "CREATE TABLE IF NOT EXISTS node_test (id INT PRIMARY KEY, val"
                                        + " VARCHAR(50))")
                        .downSql("DROP TABLE IF EXISTS node_test")
                        .build();

        // when
        var upResult = node.upTask().execute();
        var downResult = node.downTask().execute();

        // then
        assertThat(upResult.isOk()).isTrue();
        assertThat(downResult.isOk()).isTrue();
    }
}
