package io.github.kakusuke.migraphe.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpgradeHistoryCommandTest {

    private static final String JDBC_URL = "jdbc:h2:mem:upgrade_cmd;DB_CLOSE_DELAY=-1";

    @Test
    void carriesAHistoryAnOlderReleaseWroteForwardAndThenHasNothingLeftToDo(@TempDir Path tempDir)
            throws IOException, SQLException {
        PluginRegistry registry = new PluginRegistry();
        registry.loadFromClasspath();
        writeProject(tempDir);
        writePreUpgradeHistory();

        ExecutionContext context = ExecutionContext.load(tempDir, registry);

        String firstRun = captureStdout(() -> new UpgradeHistoryCommand(context).execute());

        assertThat(firstRun).contains("rename environment_id to target_id");
        assertThat(firstRun).contains("add fingerprint column");
        assertThat(firstRun).contains("Applied 7 upgrades.");
        assertThat(historyColumns()).contains("TARGET_ID", "FINGERPRINT", "NO_WAY_BACK");
        assertThat(historyColumns()).doesNotContain("ENVIRONMENT_ID");

        String secondRun = captureStdout(() -> new UpgradeHistoryCommand(context).execute());

        assertThat(secondRun).contains("The history is already up to date.");
        assertThat(secondRun).doesNotContain("Applied");
    }

    @Test
    void anotherCommandRefusesWhileTheHistoryStillNeedsAnUpgrade(@TempDir Path tempDir)
            throws IOException, SQLException {
        PluginRegistry registry = new PluginRegistry();
        registry.loadFromClasspath();
        writeProject(tempDir);
        writePreUpgradeHistory();

        ExecutionContext context = ExecutionContext.load(tempDir, registry);

        StringBuilder refusal = new StringBuilder();
        int refusedExitCode =
                captureStderrInto(refusal, () -> new StatusCommand(context, false).execute());

        assertThat(refusedExitCode).isEqualTo(1);
        assertThat(refusal.toString())
                .contains("needs 7 upgrade(s)")
                .contains("rename environment_id to target_id")
                .contains("migraphe upgrade-history");

        captureStdout(() -> new UpgradeHistoryCommand(context).execute());

        String afterUpgrade = captureStdout(() -> new StatusCommand(context, false).execute());
        assertThat(afterUpgrade).contains("001_create_users");
    }

    @Test
    void fillsTheFingerprintOfARowTheDefinitionsStillDeclare(@TempDir Path tempDir)
            throws IOException, SQLException {
        PluginRegistry registry = new PluginRegistry();
        registry.loadFromClasspath();
        writeProject(tempDir);
        writePreUpgradeHistory();
        insertLegacyApply("001_create_users");

        ExecutionContext context = ExecutionContext.load(tempDir, registry);

        captureStdout(() -> new UpgradeHistoryCommand(context).execute());

        assertThat(recordedFingerprint("001_create_users")).isNotNull();

        String status = captureStdout(() -> new StatusCommand(context, false).execute());
        assertThat(status).contains("[✓] 001_create_users");
        assertThat(status).doesNotContain("[?]");
    }

    /** Records an apply the way a release before the fingerprint column did: without one. */
    private static void insertLegacyApply(String nodeId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO migraphe_history VALUES ('legacy-1', '"
                            + nodeId
                            + "', 'h2-db', 'UP', 'SUCCESS', CURRENT_TIMESTAMP,"
                            + " 'Create users', 'DROP TABLE IF EXISTS users;', 1, NULL)");
        }
    }

    private static @Nullable String recordedFingerprint(String nodeId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet rs =
                        statement.executeQuery(
                                "SELECT fingerprint FROM migraphe_history WHERE node_id = '"
                                        + nodeId
                                        + "'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static void writePreUpgradeHistory() throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS migraphe_history");
            statement.execute(
                    """
                    CREATE TABLE migraphe_history (
                        id VARCHAR(64) PRIMARY KEY,
                        node_id VARCHAR(255) NOT NULL,
                        environment_id VARCHAR(255) NOT NULL,
                        direction VARCHAR(10) NOT NULL,
                        status VARCHAR(10) NOT NULL,
                        executed_at TIMESTAMP NOT NULL,
                        description TEXT,
                        serialized_down_task TEXT,
                        duration_ms BIGINT,
                        error_message TEXT
                    )
                    """);
        }
    }

    private static List<String> historyColumns() throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet rs =
                        statement.executeQuery(
                                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS"
                                        + " WHERE TABLE_NAME = 'MIGRAPHE_HISTORY'")) {
            while (rs.next()) {
                columns.add(rs.getString(1));
            }
        }
        return columns;
    }

    private static void writeProject(Path baseDir) throws IOException {
        Files.writeString(
                baseDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test
                history:
                  target: h2-db
                """);
        Path targetsDir = Files.createDirectories(baseDir.resolve("targets"));
        Files.writeString(
                targetsDir.resolve("h2-db.yaml"),
                """
                type: jdbc
                driver_class: org.h2.Driver
                db_label: H2
                jdbc_url: %s
                username: sa
                """
                        .formatted(JDBC_URL));
        Path tasksDir = Files.createDirectories(baseDir.resolve("tasks"));
        Files.writeString(
                tasksDir.resolve("001_create_users.yaml"),
                """
                name: Create users
                target: h2-db
                autocommit: true
                up: |
                  CREATE TABLE users (id INT PRIMARY KEY);
                down: |
                  DROP TABLE IF EXISTS users;
                """);
    }

    /** Runs {@code action} with stderr captured into {@code sink} and returns its exit code. */
    private static int captureStderrInto(
            StringBuilder sink, java.util.function.IntSupplier action) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.err;
        System.setErr(new PrintStream(baos, true, StandardCharsets.UTF_8));
        try {
            return action.getAsInt();
        } finally {
            System.setErr(original);
            sink.append(baos.toString(StandardCharsets.UTF_8));
        }
    }

    private static String captureStdout(Runnable action) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
