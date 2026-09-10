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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitCommandTest {

    private static final String JDBC_URL = "jdbc:h2:mem:init_cmd;DB_CLOSE_DELAY=-1";

    @Test
    void createsTheHistoryAndSaysSoWhenItIsAlreadyThere(@TempDir Path tempDir)
            throws IOException, SQLException {
        ExecutionContext context = loadProject(tempDir);
        dropHistory();

        String firstRun = captureStdout(() -> new InitCommand(context).execute());

        assertThat(firstRun).contains("Created the migration history.");
        assertThat(historyExists()).isTrue();

        String secondRun = captureStdout(() -> new InitCommand(context).execute());

        assertThat(secondRun).contains("The migration history already exists.");
    }

    @Test
    void aReportingCommandRefusesRatherThanCreatingTheHistoryItself(@TempDir Path tempDir)
            throws IOException, SQLException {
        ExecutionContext context = loadProject(tempDir);
        dropHistory();

        AtomicInteger exitCode = new AtomicInteger();
        String stderr =
                captureStderr(() -> exitCode.set(new StatusCommand(context, false).execute()));

        assertThat(exitCode.get()).isEqualTo(1);
        assertThat(stderr)
                .contains("the migration history has not been created here")
                .contains("migraphe init");
        // The refusal must not point at up: that answers "I cannot report" with "change the
        // database".
        assertThat(stderr).doesNotContain("migraphe up");
        assertThat(historyExists()).isFalse();
    }

    @Test
    void upCreatesTheHistoryItselfBecauseThatIsWhenAProjectGetsOne(@TempDir Path tempDir)
            throws IOException, SQLException {
        ExecutionContext context = loadProject(tempDir);
        dropHistory();

        AtomicInteger exitCode = new AtomicInteger();
        captureStdout(() -> exitCode.set(new UpCommand(context, null, true, false).execute()));

        assertThat(exitCode.get()).isZero();
        assertThat(historyExists()).isTrue();
    }

    private static ExecutionContext loadProject(Path tempDir) throws IOException {
        PluginRegistry registry = new PluginRegistry();
        registry.loadFromClasspath();
        writeProject(tempDir);
        return ExecutionContext.load(tempDir, registry);
    }

    private static void dropHistory() throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS migraphe_history");
            statement.execute("DROP TABLE IF EXISTS users");
        }
    }

    private static boolean historyExists() throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet rs =
                        statement.executeQuery(
                                "SELECT 1 FROM INFORMATION_SCHEMA.TABLES"
                                        + " WHERE TABLE_NAME = 'MIGRAPHE_HISTORY'")) {
            return rs.next();
        }
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

    private static String captureStderr(Runnable action) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.err;
        System.setErr(new PrintStream(baos, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setErr(original);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
