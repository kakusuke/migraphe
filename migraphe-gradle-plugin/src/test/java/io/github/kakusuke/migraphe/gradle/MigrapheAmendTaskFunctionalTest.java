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
    void previewShouldListTheDriftedMigrationWithoutRecordingIt() throws IOException, SQLException {
        String jdbcUrl = writeH2Project();

        runTask("migrapheUp");
        eraseRecordedFingerprints(jdbcUrl);

        BuildResult result =
                runTask("migrapheAmend", "--preview", "--migration=h2-db/001_create_users");

        assertThat(result.getOutput())
                .contains("[DRY RUN] Amend plan (history only — no database changes):")
                .contains("[?] → [✓]  h2-db/001_create_users - Create users")
                .contains("1 fingerprint would be recorded. No changes made (dry run).")
                .doesNotContain("withdrawn");
        assertThat(result.getOutput())
                .contains("a row is appended saying what the definition says");
        assertThat(recordedFingerprint(jdbcUrl)).isNull();
    }

    @Test
    void shouldRefuseToRunWithoutAMigrationAndPointAtUpgrade() throws IOException {
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

        BuildResult result = runTaskAndFail("migrapheAmend");

        assertThat(result.getOutput())
                .contains("--migration must be specified")
                .contains("./gradlew migrapheUpgradeHistory");
    }

    @Test
    void theNamedFormLeavesAnotherDriftedMigrationAlone() throws IOException, SQLException {
        String jdbcUrl = writeH2Project();
        writeSecondMigration();

        runTask("migrapheUp");
        eraseRecordedFingerprint(jdbcUrl, "h2-db/001_create_users");
        editSecondMigration();
        String editedNodeBefore = recordedFingerprint(jdbcUrl, "h2-db/002_add_index");

        runTask("migrapheAmend", "--migration=h2-db/002_add_index");

        assertThat(recordedFingerprint(jdbcUrl, "h2-db/001_create_users")).isNull();
        assertThat(recordedFingerprint(jdbcUrl, "h2-db/002_add_index"))
                .isNotEqualTo(editedNodeBefore);
    }

    @Test
    void shouldRefuseAnIdItCannotActOnAndStillAmendTheOnesItCan() throws IOException, SQLException {
        String jdbcUrl = writeH2Project();
        writeSecondMigration();
        writeThirdMigration();

        runTask("migrapheUp");
        editSecondMigration();
        Files.delete(testProjectDir.resolve("tasks/h2-db/003_add_email.yaml"));
        String editedNodeBefore = recordedFingerprint(jdbcUrl, "h2-db/002_add_index");

        BuildResult unknown = runTaskAndFail("migrapheAmend", "--migration=h2-db/001_typo");
        assertThat(unknown.getOutput()).contains("No such migration").contains("h2-db/001_typo");

        BuildResult orphanPreview =
                runTask("migrapheAmend", "--preview", "--migration=h2-db/003_add_email");
        assertThat(orphanPreview.getOutput())
                .contains("1 migration would be withdrawn. No changes made (dry run).");

        BuildResult orphan = runTask("migrapheAmend", "--migration=h2-db/003_add_email");
        assertThat(orphan.getOutput())
                .contains("[✓] → [ ]  h2-db/003_add_email")
                .contains("no longer defined")
                .contains("objects are left in the database")
                .contains("migraphe down")
                .contains("Withdrew 1 migration.")
                .doesNotContain("fingerprint");

        // A migration that already agrees is claimed too: naming one is a deliberate statement of
        // what the definitions say, and that does not become conditional on a comparison.
        BuildResult agreeing = runTask("migrapheAmend", "--migration=h2-db/001_create_users");
        assertThat(agreeing.getOutput())
                .contains("h2-db/001_create_users")
                .doesNotContain("Nothing to amend.");

        runTask("migrapheAmend", "--migration=h2-db/002_add_index");
        assertThat(recordedFingerprint(jdbcUrl, "h2-db/002_add_index"))
                .isNotEqualTo(editedNodeBefore);
    }

    private void writeThirdMigration() throws IOException {
        Files.writeString(
                testProjectDir.resolve("tasks/h2-db/003_add_email.yaml"),
                """
                name: Add email
                target: h2-db
                autocommit: true
                dependencies:
                  - h2-db/001_create_users
                up: |
                  ALTER TABLE users ADD COLUMN email VARCHAR(255);
                down: |
                  ALTER TABLE users DROP COLUMN email;
                """);
    }

    private void writeSecondMigration() throws IOException {
        Files.writeString(
                testProjectDir.resolve("tasks/h2-db/002_add_index.yaml"),
                """
                name: Add index
                target: h2-db
                autocommit: true
                dependencies:
                  - h2-db/001_create_users
                up: |
                  CREATE INDEX idx_users_id ON users(id);
                down: |
                  DROP INDEX IF EXISTS idx_users_id;
                """);
    }

    private void editSecondMigration() throws IOException {
        Files.writeString(
                testProjectDir.resolve("tasks/h2-db/002_add_index.yaml"),
                """
                name: Add index
                target: h2-db
                autocommit: true
                dependencies:
                  - h2-db/001_create_users
                up: |
                  CREATE INDEX idx_users_id ON users(id); -- reformatted
                down: |
                  DROP INDEX IF EXISTS idx_users_id;
                """);
    }

    private static void eraseRecordedFingerprint(String jdbcUrl, String nodeId)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "UPDATE migraphe_history SET fingerprint = NULL WHERE node_id = '"
                            + nodeId
                            + "'");
        }
    }

    /**
     * Returns the fingerprint on one node's latest applied row.
     *
     * <p>Amending appends rather than rewriting, so a node has more than one row once it has been
     * amended. This orders the way everything else that asks "what does the history say now"
     * orders: the newest UP that succeeded, with the id breaking a tied timestamp.
     */
    private static String recordedFingerprint(String jdbcUrl, String nodeId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT fingerprint FROM migraphe_history WHERE node_id = '"
                                        + nodeId
                                        + "' AND direction = 'UP' AND status = 'SUCCESS'"
                                        + " ORDER BY executed_at DESC, id DESC")) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    /** Runs one Migraphe task against the test project and requires the build to succeed. */
    private BuildResult runTask(String... arguments) {
        return runner(arguments).build();
    }

    /** Runs one Migraphe task against the test project and requires the build to fail. */
    private BuildResult runTaskAndFail(String... arguments) {
        return runner(arguments).buildAndFail();
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
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

    /**
     * Returns the fingerprint on the latest applied row, which may be {@code null}.
     *
     * <p>Ordered rather than "the one row": amending appends, so there is more than one after it.
     */
    private static String recordedFingerprint(String jdbcUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT fingerprint FROM migraphe_history"
                                        + " WHERE direction = 'UP' AND status = 'SUCCESS'"
                                        + " ORDER BY executed_at DESC, id DESC")) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }
}
