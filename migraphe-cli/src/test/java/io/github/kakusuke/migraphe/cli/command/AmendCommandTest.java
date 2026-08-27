package io.github.kakusuke.migraphe.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AmendCommandTest {

    private static final String JDBC_URL = "jdbc:h2:mem:amend_cmd;DB_CLOSE_DELAY=-1";

    @Test
    void shouldShowWhichStateEachNodeIsBeingMovedFrom(@TempDir Path tempDir)
            throws IOException, SQLException {
        PluginRegistry registry = new PluginRegistry();
        registry.loadFromClasspath();

        writeProject(tempDir);
        ExecutionContext applied = ExecutionContext.load(tempDir, registry);
        captureStdout(() -> new UpCommand(applied, null, true, false).execute());

        // 001 loses its recorded fingerprint, the way an upgrade from before the column looks.
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "UPDATE migraphe_history SET fingerprint = NULL"
                            + " WHERE node_id = '001_create_users'");
        }
        // 002 is edited after having been applied.
        Files.writeString(
                tempDir.resolve("tasks/002_add_index.yaml"),
                """
                name: Add index
                target: h2-db
                autocommit: true
                dependencies:
                  - 001_create_users
                up: |
                  CREATE INDEX idx_users_id ON users(id); -- reformatted
                down: |
                  DROP INDEX IF EXISTS idx_users_id;
                """);

        ExecutionContext reloaded = ExecutionContext.load(tempDir, registry);
        Command amend =
                new AmendCommand(reloaded, false, true, new ByteArrayInputStream(new byte[0]));

        String stdout = captureStdout(amend::execute);

        assertThat(stdout).contains("[?] → [✓]  001_create_users");
        assertThat(stdout).contains("[!] → [✓]  002_add_index");
        assertThat(stdout).contains("edited after it was applied");
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
        Files.writeString(
                tasksDir.resolve("002_add_index.yaml"),
                """
                name: Add index
                target: h2-db
                autocommit: true
                dependencies:
                  - 001_create_users
                up: |
                  CREATE INDEX idx_users_id ON users(id);
                down: |
                  DROP INDEX IF EXISTS idx_users_id;
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
}
