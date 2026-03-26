package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcDownTaskTest {

    private JdbcEnvironment env;

    @BeforeEach
    void setUp() throws Exception {
        env =
                JdbcEnvironment.create(
                        "testdb",
                        "jdbc:h2:mem:downtask_test;DB_CLOSE_DELAY=-1",
                        "sa",
                        "",
                        "org.h2.Driver",
                        "H2");
        try (Connection conn = env.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
            stmt.execute("CREATE TABLE t1 (id INT)");
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
    void executeWithTransaction() {
        var task = JdbcDownTask.create(env, "DROP TABLE t1", false);
        Result<TaskResult, String> result = task.execute();
        assertThat(result.isOk()).isTrue();
        assertThat(result.value().serializedDownTask()).isNull();
    }

    @Test
    void executeWithAutocommit() throws Exception {
        try (Connection conn = env.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t2 (id INT)");
        }
        var task = JdbcDownTask.create(env, "DROP TABLE t1;\nDROP TABLE t2;\n", true);
        Result<TaskResult, String> result = task.execute();
        assertThat(result.isOk()).isTrue();
    }

    @Test
    void executeFailsOnInvalidSql() {
        var task = JdbcDownTask.create(env, "INVALID SQL", false);
        Result<TaskResult, String> result = task.execute();
        assertThat(result.isErr()).isTrue();
        assertThat(result.error()).contains("Failed to execute DOWN migration");
    }

    @Test
    void descriptionIncludesDbLabel() {
        var task = JdbcDownTask.create(env, "DROP TABLE t1", false);
        assertThat(task.description()).isEqualTo("H2 DOWN migration");
    }

    @Test
    void descriptionIncludesAutocommit() {
        var task = JdbcDownTask.create(env, "DROP TABLE t1", true);
        assertThat(task.description()).isEqualTo("H2 DOWN migration (autocommit)");
    }
}
