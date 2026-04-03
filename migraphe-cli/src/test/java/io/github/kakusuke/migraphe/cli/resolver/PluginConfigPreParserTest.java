package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;

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
    void shouldReturnEmptyListWhenFileDoesNotExist() {
        Path missing = tempDir.resolve("nonexistent.yaml");
        var parser = new PluginConfigPreParser();

        List<MavenArtifactCoordinate> result = parser.parsePlugins(missing);

        assertThat(result).isEmpty();
    }
}
