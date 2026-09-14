package io.github.kakusuke.migraphe.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrapheUpgradeHistoryTaskFunctionalTest {

    @TempDir Path testProjectDir;

    @Test
    void carriesAHistoryAnOlderReleaseWroteForwardAndThenHasNothingLeftToDo()
            throws IOException, SQLException {
        String jdbcUrl = writeH2Project();
        writePreUpgradeHistory(jdbcUrl);

        BuildResult first = runner("migrapheUpgradeHistory").build();

        assertThat(first.getOutput()).contains("rename environment_id to target_id");
        assertThat(first.getOutput()).contains("Applied 7 upgrades.");
        assertThat(historyColumns(jdbcUrl)).contains("TARGET_ID", "FINGERPRINT", "NO_WAY_BACK");
        assertThat(historyColumns(jdbcUrl)).doesNotContain("ENVIRONMENT_ID");

        BuildResult second = runner("migrapheUpgradeHistory").build();

        assertThat(second.getOutput()).contains("The history is already up to date.");
        assertThat(second.getOutput()).doesNotContain("Applied");
    }

    @Test
    void anotherTaskFailsWhileTheHistoryStillNeedsAnUpgrade() throws IOException, SQLException {
        String jdbcUrl = writeH2Project();
        writePreUpgradeHistory(jdbcUrl);

        BuildResult refused = runner("migrapheStatus").buildAndFail();

        assertThat(refused.getOutput()).contains("needs 7 upgrade(s)");
        assertThat(refused.getOutput()).contains("rename environment_id to target_id");
        assertThat(refused.getOutput()).contains("./gradlew migrapheUpgradeHistory");

        runner("migrapheUpgradeHistory").build();

        BuildResult afterUpgrade = runner("migrapheStatus").build();
        assertThat(afterUpgrade.getOutput()).contains("001_create_users");
    }

    @Test
    void refusesToUpgradeAHistoryThatDoesNotExistYet() throws IOException {
        writeH2Project();

        BuildResult refused = runner("migrapheUpgradeHistory").buildAndFail();

        assertThat(refused.getOutput()).contains("has not been created here");
        assertThat(refused.getOutput()).contains("./gradlew migrapheInit");
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
    }

    /** Creates the history table in the shape a release before 0.7.0 wrote. */
    private static void writePreUpgradeHistory(String jdbcUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
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

    private static List<String> historyColumns(String jdbcUrl) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
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

    /**
     * Writes a project backed by a file-based H2 database, and returns its JDBC URL. The database
     * has to be file-backed: TestKit runs the build in its own daemon JVM, so an in-memory database
     * would be invisible to this one.
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
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), "");

        Files.writeString(
                testProjectDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test-project
                history:
                  target: h2-db
                """);

        String jdbcUrl = "jdbc:h2:file:" + testProjectDir.resolve("upgrade-db") + ";MODE=LEGACY";

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
}
