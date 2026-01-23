package io.github.kakusuke.migraphe.cli.command;

import static org.assertj.core.api.Assertions.*;

import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("NullAway.Init")
class ValidateCommandTest {

    @TempDir Path tempDir;

    private PluginRegistry pluginRegistry;
    private ByteArrayOutputStream outputStream;
    private ByteArrayOutputStream errorStream;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    void setUp() {
        pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        outputStream = new ByteArrayOutputStream();
        errorStream = new ByteArrayOutputStream();
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(outputStream));
        System.setErr(new PrintStream(errorStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void shouldReturnSuccessForValidConfiguration() throws IOException {
        // Given: 正しい設定ファイル
        createValidProject(tempDir);
        ValidateCommand command = new ValidateCommand(tempDir, pluginRegistry, false);

        // When
        int exitCode = command.execute();

        // Then
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Validation");
        assertThat(output).contains("OK");
        assertThat(output).contains("Validation successful");
    }

    @Test
    void shouldReturnFailureForMissingProjectConfig() throws IOException {
        // Given: migraphe.yaml が存在しない
        Files.createDirectories(tempDir.resolve("targets"));
        Files.createDirectories(tempDir.resolve("tasks"));
        ValidateCommand command = new ValidateCommand(tempDir, pluginRegistry, false);

        // When
        int exitCode = command.execute();

        // Then
        assertThat(exitCode).isEqualTo(1);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("FAIL");
        assertThat(output).contains("migraphe.yaml");
    }

    @Test
    void shouldReturnFailureForMissingTargetType() throws IOException {
        // Given: target の type が欠落
        createProjectWithMissingTargetType(tempDir);
        ValidateCommand command = new ValidateCommand(tempDir, pluginRegistry, false);

        // When
        int exitCode = command.execute();

        // Then
        assertThat(exitCode).isEqualTo(1);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("FAIL");
        assertThat(output).contains("type");
    }

    @Test
    void shouldDisplayErrorCountInSummary() throws IOException {
        // Given: 複数のエラー
        createProjectWithMultipleErrors(tempDir);
        ValidateCommand command = new ValidateCommand(tempDir, pluginRegistry, false);

        // When
        int exitCode = command.execute();

        // Then
        assertThat(exitCode).isEqualTo(1);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Validation failed with");
        assertThat(output).contains("errors");
    }

    @Test
    void shouldUseColorsWhenEnabled() throws IOException {
        // Given: 色が有効
        createValidProject(tempDir);
        ValidateCommand command = new ValidateCommand(tempDir, pluginRegistry, true);

        // When
        command.execute();

        // Then: 出力にANSI色コードが含まれる
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("\u001B[");
    }

    @Test
    void shouldNotUseColorsWhenDisabled() throws IOException {
        // Given: 色が無効
        createValidProject(tempDir);
        ValidateCommand command = new ValidateCommand(tempDir, pluginRegistry, false);

        // When
        command.execute();

        // Then: 出力にANSI色コードが含まれない
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).doesNotContain("\u001B[32m"); // green
        assertThat(output).doesNotContain("\u001B[31m"); // red
    }

    @Test
    void shouldDisplayCheckingSteps() throws IOException {
        // Given: 正しい設定ファイル
        createValidProject(tempDir);
        ValidateCommand command = new ValidateCommand(tempDir, pluginRegistry, false);

        // When
        command.execute();

        // Then: 各ステップが表示される
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("project configuration");
        assertThat(output).contains("targets");
        assertThat(output).contains("tasks");
        assertThat(output).contains("dependencies");
        assertThat(output).contains("graph structure");
    }

    // Helper methods

    private void createValidProject(Path baseDir) throws IOException {
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
                jdbc_url: jdbc:postgresql://localhost:5432/test
                username: test
                password: test
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

    private void createProjectWithMissingTargetType(Path baseDir) throws IOException {
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
                jdbc_url: jdbc:postgresql://localhost:5432/test
                username: test
                password: test
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);
    }

    private void createProjectWithMultipleErrors(Path baseDir) throws IOException {
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
                jdbc_url: jdbc:postgresql://localhost:5432/test
                username: test
                password: test
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);

        Path tasksDir = baseDir.resolve("tasks").resolve("test-db");
        Files.createDirectories(tasksDir);

        // エラー1: 存在しない dependency
        String task1Yaml =
                """
                name: Task 1
                target: test-db
                dependencies:
                  - missing-dep
                up: SELECT 1;
                down: SELECT 1;
                """;
        Files.writeString(tasksDir.resolve("task1.yaml"), task1Yaml);

        // エラー2: 存在しない target
        String task2Yaml =
                """
                name: Task 2
                target: nonexistent-target
                up: SELECT 1;
                down: SELECT 1;
                """;
        Files.writeString(tasksDir.resolve("task2.yaml"), task2Yaml);
    }
}
