package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginConfigPreParserTest {

    @TempDir Path tempDir;

    @Test
    void shouldReturnParsedCoordinatesWhenPluginsKeyPresent() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                plugins:
                  - io.github.kakusuke:migraphe-plugin-generator-json:0.1.0-SNAPSHOT
                project:
                  name: test
                """);
        var parser = new PluginConfigPreParser();

        List<MavenArtifactCoordinate> result = parser.parsePlugins(migrapheYaml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).groupId()).isEqualTo("io.github.kakusuke");
        assertThat(result.get(0).artifactId()).isEqualTo("migraphe-plugin-generator-json");
        assertThat(result.get(0).version()).isEqualTo("0.1.0-SNAPSHOT");
    }

    @Test
    void shouldReturnEmptyListWhenPluginsKeyAbsent() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                project:
                  name: test
                """);
        var parser = new PluginConfigPreParser();

        List<MavenArtifactCoordinate> result = parser.parsePlugins(migrapheYaml);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowFriendlyErrorWhenPluginsElementIsNotString() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                plugins:
                  - {group: foo}
                """);
        var parser = new PluginConfigPreParser();

        assertThatThrownBy(() -> parser.parsePlugins(migrapheYaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plugins[0]")
                .hasMessageContaining("LinkedHashMap");
    }

    @Test
    void shouldThrowFriendlyErrorWhenPluginsElementIsNull() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                plugins:
                  - ~
                """);
        var parser = new PluginConfigPreParser();

        assertThatThrownBy(() -> parser.parsePlugins(migrapheYaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plugins[0]")
                .hasMessageContaining("null");
    }

    @Test
    void shouldReturnEmptyListWhenPluginsValueIsNull() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(migrapheYaml, """
                plugins: ~
                """);
        var parser = new PluginConfigPreParser();

        List<MavenArtifactCoordinate> result = parser.parsePlugins(migrapheYaml);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowFriendlyErrorWhenPluginsValueIsStringScalar() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                plugins: "io.example:foo:1.0"
                """);
        var parser = new PluginConfigPreParser();

        assertThatThrownBy(() -> parser.parsePlugins(migrapheYaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plugins")
                .hasMessageContaining("list")
                .hasMessageContaining("String");
    }

    @Test
    void shouldReturnEmptyListWhenFileDoesNotExist() {
        Path missing = tempDir.resolve("nonexistent.yaml");
        var parser = new PluginConfigPreParser();

        List<MavenArtifactCoordinate> result = parser.parsePlugins(missing);

        assertThat(result).isEmpty();
    }
}
