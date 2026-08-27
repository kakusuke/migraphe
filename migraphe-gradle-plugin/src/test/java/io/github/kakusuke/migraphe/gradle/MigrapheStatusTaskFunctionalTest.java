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

class MigrapheStatusTaskFunctionalTest {

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
    void shouldMarkEveryNodePendingAndTallyThem() throws IOException {
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
        Files.writeString(
                tasksDir.resolve("002_index.yaml"),
                """
                name: Add index
                target: noop-db
                dependencies:
                  - noop-db/001_create
                up: Add an index on users.name
                """);

        BuildResult result =
                GradleRunner.create()
                        .withProjectDir(testProjectDir.toFile())
                        .withPluginClasspath()
                        .withArguments("migrapheStatus")
                        .build();

        assertThat(result.getOutput())
                .contains("Migration Status")
                .contains("[ ] noop-db/001_create - Create users")
                .contains("[ ] noop-db/002_index - Add index")
                .contains("Summary: Total: 2 | Executed: 0 | Pending: 2");
    }
}
