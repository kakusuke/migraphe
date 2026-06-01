package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcUpTaskTest {

    private JdbcEnvironment env;

    @BeforeEach
    void setUp() throws Exception {
        env =
                JdbcEnvironment.create(
                        "testdb",
                        "jdbc:h2:mem:uptask_test;DB_CLOSE_DELAY=-1",
                        "sa",
                        "",
                        "org.h2.Driver",
                        "H2");
        try (Connection conn = env.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = env.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
    }

    @Test
    void executeWithTransaction() throws Exception {
        var task = JdbcUpTask.create(env, "CREATE TABLE t1 (id INT)", "DROP TABLE t1", false);
        Result<TaskResult, String> result = task.execute();
        assertThat(result.isOk()).isTrue();
        assertThat(result.value().serializedDownTask()).isEqualTo("DROP TABLE t1");

        // Verify table was created
        try (Connection conn = env.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME ="
                                        + " 'T1'")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void executeWithAutocommit() throws Exception {
        var task =
                JdbcUpTask.create(
                        env, "CREATE TABLE t1 (id INT);\nCREATE TABLE t2 (id INT);\n", null, true);
        Result<TaskResult, String> result = task.execute();
        assertThat(result.isOk()).isTrue();
        assertThat(result.value().serializedDownTask()).isNull();
    }

    @Test
    void executeFailsOnInvalidSql() {
        var task = JdbcUpTask.create(env, "INVALID SQL STATEMENT", null, false);
        Result<TaskResult, String> result = task.execute();
        assertThat(result.isErr()).isTrue();
        assertThat(result.error()).contains("Failed to execute UP migration");
    }

    @Test
    void descriptionIncludesDbLabel() {
        var task = JdbcUpTask.create(env, "SELECT 1", null, false);
        assertThat(task.description()).isEqualTo("H2 UP migration");
    }

    @Test
    void descriptionIncludesAutocommit() {
        var task = JdbcUpTask.create(env, "SELECT 1", null, true);
        assertThat(task.description()).isEqualTo("H2 UP migration (autocommit)");
    }

    @Test
    void withoutDownSql() {
        var task = JdbcUpTask.create(env, "CREATE TABLE t1 (id INT)", null, false);
        Result<TaskResult, String> result = task.execute();
        assertThat(result.isOk()).isTrue();
        assertThat(result.value().serializedDownTask()).isNull();
    }

    @Test
    void sqlContentProviderReturnsSql() {
        var task = JdbcUpTask.create(env, "CREATE TABLE t1 (id INT)", null, false);
        assertThat(task.sqlContent()).isEqualTo("CREATE TABLE t1 (id INT)");
    }

    @Test
    void executeWithTransactionMultipleStatements() throws Exception {
        var task =
                JdbcUpTask.create(
                        env,
                        "CREATE TABLE tx1 (id INT);\nCREATE TABLE tx2 (id INT);\n",
                        null,
                        false);
        Result<TaskResult, String> result = task.execute();
        assertThat(result.isOk()).isTrue();

        try (Connection conn = env.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME IN"
                                        + " ('TX1', 'TX2')")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }
}
