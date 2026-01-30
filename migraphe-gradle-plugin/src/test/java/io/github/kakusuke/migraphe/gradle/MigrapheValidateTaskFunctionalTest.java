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

class MigrapheValidateTaskFunctionalTest {

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
    void shouldSucceedWithMinimalProject() throws IOException {
        // migraphe.yaml のみの最小プロジェクト（targets/tasks なし）
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
                        .withArguments("migrapheValidate")
                        .build();

        assertThat(result.getOutput()).contains("Validation successful");
    }

    @Test
    void shouldFailWithMissingProjectConfig() {
        // migraphe.yaml がない場合
        BuildResult result =
                GradleRunner.create()
                        .withProjectDir(testProjectDir.toFile())
                        .withPluginClasspath()
                        .withArguments("migrapheValidate")
                        .forwardOutput()
                        .buildAndFail();

        assertThat(result.getOutput()).contains("Validation failed");
    }

    @Test
    void shouldReportUnknownPluginType() throws IOException {
        // TestKit 環境では PostgreSQL プラグインが利用できないため、unknown type として報告される
        createProjectWithTargets(testProjectDir);

        BuildResult result =
                GradleRunner.create()
                        .withProjectDir(testProjectDir.toFile())
                        .withPluginClasspath()
                        .withArguments("migrapheValidate")
                        .forwardOutput()
                        .buildAndFail();

        assertThat(result.getOutput()).contains("Unknown plugin type 'postgresql'");
        assertThat(result.getOutput()).contains("Validation failed");
    }

    @Test
    void shouldSupportCustomBaseDir() throws IOException {
        // カスタムベースディレクトリ（最小プロジェクト）
        Path dbDir = testProjectDir.resolve("db");
        Files.createDirectories(dbDir);

        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                """;
        Files.writeString(dbDir.resolve("migraphe.yaml"), projectYaml);

        String buildScript =
                """
                plugins {
                    id("io.github.kakusuke.migraphe")
                }

                migraphe {
                    baseDir.set(layout.projectDirectory.dir("db"))
                }
                """;
        Files.writeString(testProjectDir.resolve("build.gradle.kts"), buildScript);

        BuildResult result =
                GradleRunner.create()
                        .withProjectDir(testProjectDir.toFile())
                        .withPluginClasspath()
                        .withArguments("migrapheValidate")
                        .build();

        assertThat(result.getOutput()).contains("Validation successful");
    }

    @Test
    void shouldReportMultipleErrors() throws IOException {
        // 複数のエラーがある場合
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                """;
        Files.writeString(testProjectDir.resolve("migraphe.yaml"), projectYaml);

        // targets/test-db.yaml に type がない
        Path targetsDir = testProjectDir.resolve("targets");
        Files.createDirectories(targetsDir);
        Files.writeString(
                targetsDir.resolve("test-db.yaml"), "jdbc_url: jdbc:postgresql://localhost");

        BuildResult result =
                GradleRunner.create()
                        .withProjectDir(testProjectDir.toFile())
                        .withPluginClasspath()
                        .withArguments("migrapheValidate")
                        .forwardOutput()
                        .buildAndFail();

        assertThat(result.getOutput()).contains("Missing required property 'type'");
        assertThat(result.getOutput()).contains("Validation failed");
    }

    @Test
    void shouldSupportVariablesDsl() throws IOException {
        // variables DSL を使用した build.gradle.kts でビルドが成功すること
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                """;
        Files.writeString(testProjectDir.resolve("migraphe.yaml"), projectYaml);

        String buildScript =
                """
                plugins {
                    id("io.github.kakusuke.migraphe")
                }

                migraphe {
                    variables.set(mapOf(
                        "DB_HOST" to "localhost",
                        "DB_USER" to "testuser"
                    ))
                }
                """;
        Files.writeString(testProjectDir.resolve("build.gradle.kts"), buildScript);

        BuildResult result =
                GradleRunner.create()
                        .withProjectDir(testProjectDir.toFile())
                        .withPluginClasspath()
                        .withArguments("migrapheValidate")
                        .build();

        assertThat(result.getOutput()).contains("Validation successful");
    }

    private void createProjectWithTargets(Path baseDir) throws IOException {
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                """;
        Files.writeString(baseDir.resolve("migraphe.yaml"), projectYaml);

        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);

        String targetYaml =
                """
                type: postgresql
                jdbc_url: jdbc:postgresql://localhost:5432/testdb
                username: testuser
                password: testpass
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);

        Path tasksDir = baseDir.resolve("tasks").resolve("test-db");
        Files.createDirectories(tasksDir);

        String taskYaml =
                """
                name: Create users table
                target: test-db
                up: CREATE TABLE users (id SERIAL PRIMARY KEY);
                down: DROP TABLE users;
                """;
        Files.writeString(tasksDir.resolve("001_create_users.yaml"), taskYaml);
    }
}
