package io.github.kakusuke.migraphe.gradle;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the guards {@code migrapheDown} inherits from {@code DownService}.
 *
 * <p>These used to live inside the CLI command, so {@code ./gradlew migrapheDown --all} succeeded
 * while quietly rolling back nothing at all. The equivalent CLI assertions are in {@code MainTest};
 * both front ends have to be shown to refuse or the next guard silently reaches only one of them
 * again.
 */
class MigrapheDownTaskFunctionalTest {

    private static final String NO_WAY_BACK = "the rows cannot be reconstructed";

    @TempDir Path testProjectDir;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), "");
    }

    @Test
    void shouldReportWhatAFullRollbackHadToLeaveBehind() throws IOException {
        writeH2Project();

        runTask("migrapheUp");
        BuildResult result = runTaskAndFail("migrapheDown", "--all");

        assertThat(result.getOutput())
                .contains("2 applied migrations cannot be rolled back")
                .contains("h2-db/002_b — no way back: " + NO_WAY_BACK);
    }

    @Test
    void shouldRefuseWhileSomethingAppliedIsNoLongerDefined() throws IOException {
        writeH2Project();

        runTask("migrapheUp");
        Files.delete(testProjectDir.resolve("tasks/h2-db/002_b.yaml"));

        BuildResult result = runTaskAndFail("migrapheDown", "--all");

        assertThat(result.getOutput())
                .contains("applied migration(s) are no longer defined")
                .contains("h2-db/002_b")
                .doesNotContain("Executing rollback...");
    }

    @Test
    void shouldReportNothingToRollbackWhenNothingHasBeenApplied() throws IOException {
        writeNoopProject();

        BuildResult result = runTask("migrapheDown", "--all");

        assertThat(result.getOutput()).contains("No migrations to rollback.");
    }

    /**
     * Writes a project whose migrations run against a file-backed H2 database: {@code 002_b}
     * depends on {@code 001_a} and declares that it cannot be rolled back, so neither of the two
     * can be. The database has to be file-backed — TestKit runs the build in its own daemon JVM, so
     * an in-memory database would be invisible to a second run.
     */
    private void writeH2Project() throws IOException {
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
                        .formatted(
                                "jdbc:h2:file:"
                                        + testProjectDir.resolve("down-db")
                                        + ";MODE=LEGACY"));

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
        Files.writeString(
                tasksDir.resolve("002_b.yaml"),
                """
                name: Create b
                target: h2-db
                autocommit: true
                dependencies:
                  - h2-db/001_a
                up: |
                  CREATE TABLE t_b (id INT PRIMARY KEY);
                no_way_back: %s
                """
                        .formatted(NO_WAY_BACK));
    }

    /** Writes a project that touches no database, for the cases that need no applied history. */
    private void writeNoopProject() throws IOException {
        Files.writeString(
                testProjectDir.resolve("build.gradle.kts"),
                """
                plugins {
                    id("io.github.kakusuke.migraphe")
                }
                """);
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
                down: Drop the users table
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
