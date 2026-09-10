package io.github.kakusuke.migraphe.gradle;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the guards {@code migrapheRebuild} inherits from {@code RebuildService}.
 *
 * <p>The task had no test at all while four refusals were added to it, so each one reached the CLI
 * and was assumed to reach here. The equivalent CLI assertions are in {@code RebuildCommandTest};
 * both front ends have to be shown to refuse or the next guard silently reaches only one of them.
 *
 * <p><strong>Three of the four are here; the fourth is deliberately not.</strong> A refusal for a
 * plugin that cannot report what it applied needs a plugin that cannot, and every plugin these
 * tests can reach folds its own SQL and always answers. Reaching it would mean shipping a
 * deliberately broken plugin jar as a fixture, which buys one message assertion and adds an
 * artifact whose only purpose is to be wrong. The CLI covers that branch, where a test double
 * stands in for the plugin without one being built.
 */
class MigrapheRebuildTaskFunctionalTest {

    @TempDir Path testProjectDir;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), "");
    }

    @Test
    void shouldRefuseWhileAnAppliedRowNamesATargetTheProjectNoLongerConfigures()
            throws IOException {
        writeTwoTargetProject();

        runTask("migrapheUp");

        // The second target is taken out of the configuration while its migration is still applied.
        Files.delete(testProjectDir.resolve("tasks/archive/001_audit.yaml"));
        Files.delete(testProjectDir.resolve("targets/archive.yaml"));

        BuildResult result = runTaskAndFail("migrapheRebuild");

        assertThat(result.getOutput())
                .contains("name a target this project no longer configures")
                .contains("archive/001_audit");
    }

    @Test
    void shouldNameBothHalvesOfTheRepairWhenARowTheDefinitionsLostCannotBeRead() throws Exception {
        writeTwoTargetProject();

        runTask("migrapheUp");

        // What an upgrade leaves behind for a migration whose task file was then deleted: the row
        // records no fingerprint, and no definition names it any more.
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "UPDATE migraphe_history SET fingerprint = NULL"
                            + " WHERE node_id = 'archive/001_audit'");
        }
        Files.delete(testProjectDir.resolve("tasks/archive/001_audit.yaml"));

        BuildResult result = runTaskAndFail("migrapheRebuild");

        // The row records nothing that can be read at face value, and no task file declares it
        // any more, so the remedy named is the one that withdraws it — in the invocation this
        // build understands, not the CLI's.
        assertThat(result.getOutput())
                .contains("[?] archive/001_audit")
                .contains("--migration=")
                .doesNotContain("migraphe amend");
    }

    @Test
    void shouldRefuseWhenSomethingThatHasToComeDownDeclaresItCannot() throws IOException {
        writeTwoTargetProject();
        Files.writeString(
                testProjectDir.resolve("tasks/h2-db/002_b.yaml"),
                """
                name: Fill a
                target: h2-db
                dependencies:
                  - h2-db/001_a
                autocommit: true
                up: |
                  INSERT INTO t_a (id) VALUES (1);
                no_way_back: the rows cannot be reconstructed
                """);

        runTask("migrapheUp");

        // 001_a is edited after it was applied, so rebuilding it means taking 002_b out first —
        // and its author has said that cannot be done.
        Files.writeString(
                testProjectDir.resolve("tasks/h2-db/001_a.yaml"),
                """
                name: Create a
                target: h2-db
                autocommit: true
                up: |
                  CREATE TABLE t_a (id INT PRIMARY KEY, label VARCHAR(20));
                down: |
                  DROP TABLE IF EXISTS t_a;
                """);

        BuildResult result = runTaskAndFail("migrapheRebuild");

        assertThat(result.getOutput())
                .contains("have to be rolled back to rebuild, and cannot be")
                .contains("h2-db/002_b");
    }

    private String jdbcUrl() {
        return "jdbc:h2:file:" + testProjectDir.resolve("rebuild-db") + ";MODE=LEGACY";
    }

    private void writeTwoTargetProject() throws IOException {
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
                  name: rebuild-test
                history:
                  target: h2-db
                """);

        String jdbcUrl = jdbcUrl();
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
        Files.writeString(
                targetsDir.resolve("archive.yaml"),
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
                tasksDir.resolve("001_a.yaml"),
                """
                name: Create a
                target: h2-db
                autocommit: true
                up: |
                  CREATE TABLE t_a (id INT PRIMARY KEY);
                down: |
                  DROP TABLE IF EXISTS t_a;
                """);

        Path archiveTasks = testProjectDir.resolve("tasks/archive");
        Files.createDirectories(archiveTasks);
        Files.writeString(
                archiveTasks.resolve("001_audit.yaml"),
                """
                name: Create audit
                target: archive
                autocommit: true
                up: |
                  CREATE TABLE t_audit (id INT PRIMARY KEY);
                down: |
                  DROP TABLE IF EXISTS t_audit;
                """);
    }

    private BuildResult runTask(String... arguments) {
        return runner(arguments).build();
    }

    private BuildResult runTaskAndFail(String... arguments) {
        return runner(arguments).buildAndFail();
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
    }
}
