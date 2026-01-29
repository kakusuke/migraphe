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

class MigraphePluginApplyTest {

    @TempDir Path testProjectDir;

    @BeforeEach
    void setUp() throws IOException {
        // build.gradle.kts を生成
        String buildScript =
                """
                plugins {
                    id("io.github.kakusuke.migraphe")
                }
                """;
        Files.writeString(testProjectDir.resolve("build.gradle.kts"), buildScript);

        // settings.gradle.kts を生成（空でOK）
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), "");
    }

    @Test
    void shouldApplyPluginSuccessfully() {
        BuildResult result =
                GradleRunner.create()
                        .withProjectDir(testProjectDir.toFile())
                        .withPluginClasspath()
                        .withArguments("tasks", "--group=migraphe")
                        .build();

        assertThat(result.getOutput()).contains("migrapheValidate");
        assertThat(result.getOutput()).contains("migrapheStatus");
        assertThat(result.getOutput()).contains("migrapheUp");
        assertThat(result.getOutput()).contains("migrapheDown");
    }

    @Test
    void shouldRegisterMigrapheExtension() {
        // Extension が正しく登録されていればタスク一覧が表示される
        BuildResult result =
                GradleRunner.create()
                        .withProjectDir(testProjectDir.toFile())
                        .withPluginClasspath()
                        .withArguments("tasks")
                        .build();

        assertThat(result.getOutput()).contains("Migraphe tasks");
    }

    @Test
    void shouldRegisterMigraphePluginConfiguration() throws IOException {
        // migraphePlugin configuration が使える
        String buildScript =
                """
                plugins {
                    id("io.github.kakusuke.migraphe")
                }

                configurations {
                    named("migraphePlugin")
                }
                """;
        Files.writeString(testProjectDir.resolve("build.gradle.kts"), buildScript);

        BuildResult result =
                GradleRunner.create()
                        .withProjectDir(testProjectDir.toFile())
                        .withPluginClasspath()
                        .withArguments("tasks")
                        .build();

        // ビルドが成功すれば configuration は存在する
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
    }
}
