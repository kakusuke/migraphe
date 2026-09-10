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

    private JdbcTarget target;

    @BeforeEach
    void setUp() throws Exception {
        target =
                JdbcTarget.create(
                        "testdb",
                        "jdbc:h2:mem:uptask_test;DB_CLOSE_DELAY=-1",
                        "sa",
                        "",
                        "org.h2.Driver",
                        "H2");
        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
    }

    @Test
    void executeWithTransaction() throws Exception {
        var task = JdbcUpTask.create(target, "CREATE TABLE t1 (id INT)", "DROP TABLE t1", false);
        Result<TaskResult, String> result = task.execute();
        assertThat(result.isOk()).isTrue();
        assertThat(result.value().serializedDownTask()).isEqualTo("DROP TABLE t1");

        // Verify table was created
        try (Connection conn = target.createConnection();
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
                        target,
                        "CREATE TABLE t1 (id INT);\nCREATE TABLE t2 (id INT);\n",
                        null,
                        true);
        Result<TaskResult, String> result = task.execute();
        assertThat(result.isOk()).isTrue();
        assertThat(result.value().serializedDownTask()).isNull();
    }

    @Test
    void executeFailsOnInvalidSql() {
        var task = JdbcUpTask.create(target, "INVALID SQL STATEMENT", null, false);
        Result<TaskResult, String> result = task.execute();
        assertThat(result.isErr()).isTrue();
        assertThat(result.error()).contains("Failed to execute UP migration");
    }

    @Test
    void recordsTheRollbacksTransactionModeSoItCanBeReplayedLater() {
        var autocommittingRollback =
                JdbcUpTask.create(target, "CREATE TABLE t1 (id INT)", "DROP TABLE t1", false, true);
        var transactionalRollback =
                JdbcUpTask.create(
                        target, "CREATE TABLE t2 (id INT)", "DROP TABLE t2", false, false);
        var noRollback = JdbcUpTask.create(target, "CREATE TABLE t3 (id INT)", null, false, true);

        assertThat(autocommittingRollback.execute().value().pluginMetadata())
                .isEqualTo("autocommit.down=true\n");
        assertThat(transactionalRollback.execute().value().pluginMetadata())
                .isEqualTo("autocommit.down=false\n");
        assertThat(noRollback.execute().value().pluginMetadata()).isNull();
    }

    @Test
    void descriptionIncludesDbLabel() {
        var task = JdbcUpTask.create(target, "SELECT 1", null, false);
        assertThat(task.description()).isEqualTo("H2 UP migration");
    }

    @Test
    void descriptionIncludesAutocommit() {
        var task = JdbcUpTask.create(target, "SELECT 1", null, true);
        assertThat(task.description()).isEqualTo("H2 UP migration (autocommit)");
    }

    @Test
    void withoutDownSql() {
        var task = JdbcUpTask.create(target, "CREATE TABLE t1 (id INT)", null, false);
        Result<TaskResult, String> result = task.execute();
        assertThat(result.isOk()).isTrue();
        assertThat(result.value().serializedDownTask()).isNull();
    }

    @Test
    void sqlContentProviderReturnsSql() {
        var task = JdbcUpTask.create(target, "CREATE TABLE t1 (id INT)", null, false);
        assertThat(task.sqlContent()).isEqualTo("CREATE TABLE t1 (id INT)");
    }

    @Test
    void executeWithTransactionMultipleStatements() throws Exception {
        var task =
                JdbcUpTask.create(
                        target,
                        "CREATE TABLE tx1 (id INT);\nCREATE TABLE tx2 (id INT);\n",
                        null,
                        false);
        Result<TaskResult, String> result = task.execute();
        assertThat(result.isOk()).isTrue();

        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME IN"
                                        + " ('TX1', 'TX2')")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void exposesTheRollbackPayloadItWouldRecordWithoutExecuting() throws Exception {
        var task =
                JdbcUpTask.create(
                        target, "CREATE TABLE t1 (id INT);", "DROP TABLE t1;", false, true);

        assertThat(task.serializedDownTask()).isEqualTo("DROP TABLE t1;");
        assertThat(task.pluginMetadata()).isEqualTo("autocommit.down=true\n");

        TaskResult executed = task.execute().value();
        assertThat(executed.serializedDownTask()).isEqualTo(task.serializedDownTask());
        assertThat(executed.pluginMetadata()).isEqualTo(task.pluginMetadata());
    }

    @Test
    void reportsNoRollbackPayloadWhenTheMigrationIsIrreversible() {
        var task = JdbcUpTask.create(target, "CREATE TABLE t2 (id INT);", null, false);

        assertThat(task.serializedDownTask()).isNull();
        assertThat(task.pluginMetadata()).isNull();
    }

    @Test
    void signatureCoversTheForwardDirectionOnlyEvenThoughTheTaskCarriesTheRollback() {
        var task =
                JdbcUpTask.create(
                        target, "CREATE TABLE t1 (id INT);", "DROP TABLE t1;", false, true);

        assertThat(task.signature())
                .isEqualTo(
                        JdbcUpTask.create(
                                        target,
                                        "CREATE TABLE t1 (id INT);",
                                        "DROP TABLE something_else;",
                                        false,
                                        false)
                                .signature());
        assertThat(task.signature())
                .isNotEqualTo(
                        JdbcUpTask.create(
                                        target,
                                        "CREATE TABLE t2 (id INT);",
                                        "DROP TABLE t1;",
                                        false,
                                        true)
                                .signature());
        assertThat(task.signature())
                .isNotEqualTo(
                        JdbcUpTask.create(
                                        target,
                                        "CREATE TABLE t1 (id INT);",
                                        "DROP TABLE t1;",
                                        true,
                                        true)
                                .signature());
        assertThat(task.signature())
                .isEqualTo(
                        JdbcUpTask.create(
                                        target,
                                        "  CREATE TABLE t1 (id INT);  ",
                                        "DROP TABLE t1;",
                                        false,
                                        true)
                                .signature());
    }

    @Test
    void signatureKeepsSqlEndingInTheModeNameApartFromTheModeBeingSet() {
        var sqlEndingInTheModeName =
                JdbcUpTask.create(target, "CREATE TABLE t1 (id INT);\nautocommit", null, false);
        var modeSet = JdbcUpTask.create(target, "CREATE TABLE t1 (id INT);", null, true);

        assertThat(sqlEndingInTheModeName.signature()).isNotEqualTo(modeSet.signature());
    }
}
