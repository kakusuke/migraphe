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
class GenerateCommandTest {

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
    void shouldReturnSuccessWhenNoGeneratorsConfigured() throws IOException {
        // Given: generators セクションがない設定
        createProjectWithoutGenerators(tempDir);
        GenerateCommand command = new GenerateCommand(tempDir, pluginRegistry, null, false);

        // When
        int exitCode = command.execute();

        // Then
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("No generators configured");
    }

    @Test
    void shouldReturnFailureWhenGeneratorPluginNotFound() throws IOException {
        // Given: 存在しない generator type を指定
        createProjectWithUnknownGeneratorType(tempDir);
        GenerateCommand command = new GenerateCommand(tempDir, pluginRegistry, null, false);

        // When
        int exitCode = command.execute();

        // Then
        assertThat(exitCode).isEqualTo(1);
        String errOutput = errorStream.toString(StandardCharsets.UTF_8);
        assertThat(errOutput).contains("Generator plugin not found");
    }

    @Test
    void shouldFilterByNameWhenSpecified() throws IOException {
        // Given: 2つの generator 設定があり、1つだけ名前フィルターで指定
        createProjectWithTwoGenerators(tempDir);
        GenerateCommand command =
                new GenerateCommand(tempDir, pluginRegistry, "non-existent-name", false);

        // When: 存在しない名前でフィルターすると、何も実行されない（プラグインエラーにならない）
        int exitCode = command.execute();

        // Then: フィルターにマッチしないため成功（何も実行されない）
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void shouldReturnFailureWhenEnvironmentNotFound() throws IOException {
        // Given: generator の target が存在しない
        createProjectWithMissingTarget(tempDir);
        GenerateCommand command = new GenerateCommand(tempDir, pluginRegistry, null, false);

        // When
        int exitCode = command.execute();

        // Then
        assertThat(exitCode).isEqualTo(1);
        String errOutput = errorStream.toString(StandardCharsets.UTF_8);
        assertThat(errOutput).contains("Environment not found");
    }

    // Helper methods

    private void createProjectWithoutGenerators(Path baseDir) throws IOException {
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
        String targetYaml = """
                type: noop
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);
    }

    private void createProjectWithUnknownGeneratorType(Path baseDir) throws IOException {
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                generators:
                  - name: schema-doc
                    type: unknown-type
                    target: test-db
                    output-dir: docs/schema
                """;
        Files.writeString(baseDir.resolve("migraphe.yaml"), projectYaml);

        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);
        String targetYaml = """
                type: noop
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);
    }

    private void createProjectWithTwoGenerators(Path baseDir) throws IOException {
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                generators:
                  - name: schema-doc1
                    type: unknown-type1
                    target: test-db
                    output-dir: docs/schema1
                  - name: schema-doc2
                    type: unknown-type2
                    target: test-db
                    output-dir: docs/schema2
                """;
        Files.writeString(baseDir.resolve("migraphe.yaml"), projectYaml);

        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);
        String targetYaml = """
                type: noop
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);
    }

    private void createProjectWithMissingTarget(Path baseDir) throws IOException {
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                generators:
                  - name: schema-doc
                    type: unknown-type
                    target: nonexistent-target
                    output-dir: docs/schema
                """;
        Files.writeString(baseDir.resolve("migraphe.yaml"), projectYaml);

        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);
        String targetYaml = """
                type: noop
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);
    }
}
