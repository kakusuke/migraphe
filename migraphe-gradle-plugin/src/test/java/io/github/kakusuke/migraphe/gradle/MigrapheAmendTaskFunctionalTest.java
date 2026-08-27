package io.github.kakusuke.migraphe.gradle;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrapheAmendTaskFunctionalTest {

    @TempDir Path testProjectDir;

    @BeforeEach
    void setUp() throws IOException {
        String buildScript =
                """
                plugins {
                    id("io.github.kakusuke.migraphe")
                }
                """;
        Files.writeString(testProjectDir.resolve("build.gradle.kts"), buildScript);
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), "");
    }

    @Test
    void shouldReportNothingToAmendWhenNoMigrationHasBeenApplied() throws IOException {
        Files.writeString(
                testProjectDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test-project
                history:
                  target: noop-db
                """);

        Path targetsDir = testProjectDir.resolve("targets");
        Files.createDirectories(targetsDir);
        Files.writeString(targetsDir.resolve("noop-db.yaml"), "type: noop\n");

        Path tasksDir = testProjectDir.resolve("tasks/noop-db");
        Files.createDirectories(tasksDir);
        Files.writeString(
                tasksDir.resolve("001_create.yaml"),
                """
                name: Create users
                target: noop-db
                up: Create the users table
                """);

        BuildResult result = runTask("migrapheAmend");

        assertThat(result.getOutput()).contains("Nothing to amend.").doesNotContain("Amend plan");
    }

    @Test
    void previewShouldListTheDriftedMigrationWithoutRecordingIt() throws IOException, SQLException {
        String jdbcUrl = writeH2Project();

        runTask("migrapheUp");
        eraseRecordedFingerprints(jdbcUrl);

        BuildResult result = runTask("migrapheAmend", "--preview");

        assertThat(result.getOutput())
                .contains("[DRY RUN] Amend plan (history only — no database changes):")
                .contains("[?] → [✓]  h2-db/001_create_users - Create users")
                .contains("1 fingerprint would be recorded. No changes made (dry run).");
        assertThat(recordedFingerprint(jdbcUrl)).isNull();
    }

    /** Runs one Migraphe task against the test project and requires the build to succeed. */
    private BuildResult runTask(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .build();
    }

    /**
     * Writes a project whose single migration runs against a file-backed H2 database, and returns
     * its JDBC URL. The database has to be file-backed: TestKit runs the build in its own daemon
     * JVM, so an in-memory database would be invisible to this one.
     */
    private String writeH2Project() throws IOException {
        String jdbcPluginClasspath = System.getProperty("jdbc.plugin.classpath");
        assertThat(jdbcPluginClasspath).as("jdbc.plugin.classpath system property").isNotNull();

        String filesArgs =
                Arrays.stream(jdbcPluginClasspath.split(File.pathSeparator))
                        .map(path -> "\"" + path.replace("\\", "\\\\") + "\"")
                        .collect(Collectors.joining(", "));

        Files.writeString(
                testProjectDir.resolve("build.gradle.kts"),
                """
                plugins {
                    id("io.github.kakusuke.migraphe")
                }
                dependencies {
                    migraphePlugin(files(%s))
                }
                """
                        .formatted(filesArgs));

        Files.writeString(
                testProjectDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test-project
                history:
                  target: h2-db
                """);

        String jdbcUrl = "jdbc:h2:file:" + testProjectDir.resolve("amend-db") + ";MODE=LEGACY";

        Path targetsDir = testProjectDir.resolve("targets");
        Files.createDirectories(targetsDir);
        Files.writeString(
                targetsDir.resolve("h2-db.yaml"),
                """
                type: jdbc
                driver_class: org.h2.Driver
                db_label: H2
                jdbc_url: %s
                username: sa
                """
                        .formatted(jdbcUrl));

        Path tasksDir = testProjectDir.resolve("tasks/h2-db");
        Files.createDirectories(tasksDir);
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

        return jdbcUrl;
    }

    /**
     * Clears every recorded fingerprint, which is what an upgrade from before the column looks
     * like.
     */
    private static void eraseRecordedFingerprints(String jdbcUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE migraphe_history SET fingerprint = NULL");
        }
    }

    /** Returns the fingerprint of the one history row, which may be {@code null}. */
    private static String recordedFingerprint(String jdbcUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery("SELECT fingerprint FROM migraphe_history")) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }
}
