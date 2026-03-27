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

class MigrapheGenerateTaskFunctionalTest {

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
    void shouldSucceedWithNoGeneratorsConfigured() throws IOException {
        // ジェネレーター設定なしの最小プロジェクト
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                """;
        Files.writeString(testProjectDir.resolve("migraphe.yaml"), projectYaml);

        BuildResult result =
                GradleRunner.create()
                        .withProjectDir(testProjectDir.toFile())
                        .withPluginClasspath()
                        .withArguments("migrapheGenerate")
                        .build();

        assertThat(result.getOutput()).contains("No generators configured");
    }

    @Test
    void shouldBeRegisteredInTaskList() {
        BuildResult result =
                GradleRunner.create()
                        .withProjectDir(testProjectDir.toFile())
                        .withPluginClasspath()
                        .withArguments("tasks", "--group=migraphe")
                        .build();

        assertThat(result.getOutput()).contains("migrapheGenerate");
    }
}
