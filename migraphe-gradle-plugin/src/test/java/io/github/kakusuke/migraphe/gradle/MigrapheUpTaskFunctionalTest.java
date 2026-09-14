package io.github.kakusuke.migraphe.gradle;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the guards {@code migrapheUp} inherits from {@code UpService}.
 *
 * <p>These used to live inside the CLI command, so the Gradle task applied what the CLI refused.
 * The equivalent CLI assertions are in {@code MainTest}; both front ends have to be shown to refuse
 * or the next guard silently reaches only one of them again.
 */
class MigrapheUpTaskFunctionalTest {

    @TempDir Path testProjectDir;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(
                testProjectDir.resolve("build.gradle.kts"),
                """
                plugins {
                    id("io.github.kakusuke.migraphe")
                }
                """);
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), "");
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
        Files.createDirectories(testProjectDir.resolve("tasks/noop-db"));
    }

    @Test
    void shouldRefuseATaskThatNeitherRollsBackNorSaysWhyNot() throws IOException {
        writeTask(
                "001_create.yaml",
                """
                name: Create users
                target: noop-db
                up: Create the users table
                """);

        BuildResult result = runTaskAndFail("migrapheUp");

        assertThat(result.getOutput())
                .contains("noop-db/001_create")
                .contains("no_way_back")
                .doesNotContain("Executing migrations...");
    }

    @Test
    void shouldRefuseATaskThatDependsOnAMigrationThatIsNotDefined() throws IOException {
        writeTask(
                "002_index.yaml",
                """
                name: Add index
                target: noop-db
                dependencies:
                  - noop-db/001_create
                up: Add an index on users.name
                down: Drop the index
                """);

        BuildResult result = runTaskAndFail("migrapheUp");

        assertThat(result.getOutput())
                .contains("noop-db/002_index → noop-db/001_create")
                .doesNotContain("Executing migrations...");
    }

    @Test
    void shouldExecuteWhenEveryTaskDeclaresARollback() throws IOException {
        writeTask(
                "001_create.yaml",
                """
                name: Create users
                target: noop-db
                up: Create the users table
                down: Drop the users table
                """);

        BuildResult result = runTask("migrapheUp");

        assertThat(result.getOutput()).contains("Executing migrations...");
    }

    private void writeTask(String fileName, String content) throws IOException {
        Files.writeString(testProjectDir.resolve("tasks/noop-db").resolve(fileName), content);
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
