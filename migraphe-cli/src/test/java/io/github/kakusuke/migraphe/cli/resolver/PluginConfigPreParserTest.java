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
    void shouldThrowFriendlyErrorWhenMapEntryMissesCoordinateKey() throws IOException {
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
                .hasMessageContaining("coordinate");
    }

    @Test
    void shouldThrowFriendlyErrorWhenPluginsElementIsUnsupportedScalar() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                plugins:
                  - 42
                """);
        var parser = new PluginConfigPreParser();

        assertThatThrownBy(() -> parser.parsePlugins(migrapheYaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plugins[0]")
                .hasMessageContaining("Integer");
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
    void shouldIncludeActualValueInErrorWhenPluginsValueIsStringScalar() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                plugins: "io.example:foo:1.0"
                """);
        var parser = new PluginConfigPreParser();

        assertThatThrownBy(() -> parser.parsePlugins(migrapheYaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("io.example:foo:1.0");
    }

    @Test
    void shouldReturnEmptyListWhenFileDoesNotExist() {
        Path missing = tempDir.resolve("nonexistent.yaml");
        var parser = new PluginConfigPreParser();

        List<MavenArtifactCoordinate> result = parser.parsePlugins(missing);

        assertThat(result).isEmpty();
    }

    @Test
    void parseReturnsCoordinateOnlyPluginsWithEmptyRepositories() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                plugins:
                  - io.github.kakusuke:migraphe-plugin-generator-json:0.1.0-SNAPSHOT
                """);
        var parser = new PluginConfigPreParser();

        PluginConfigParseResult result = parser.parse(migrapheYaml);

        assertThat(result.repositories()).isEmpty();
        assertThat(result.plugins()).hasSize(1);
        assertThat(result.plugins().get(0).coordinate().artifactId())
                .isEqualTo("migraphe-plugin-generator-json");
        assertThat(result.plugins().get(0).repositoryRef()).isEmpty();
    }

    @Test
    void parseReturnsEmptyResultWhenFileMissing() {
        Path missing = tempDir.resolve("nonexistent.yaml");
        var parser = new PluginConfigPreParser();

        PluginConfigParseResult result = parser.parse(missing);

        assertThat(result.repositories()).isEmpty();
        assertThat(result.plugins()).isEmpty();
    }

    @Test
    void parseAcceptsMapFormPluginEntryWithRepositoryRef() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                plugins:
                  - coordinate: com.github.alice:migraphe-plugin-foo:v1.2.0
                    repository: jitpack
                """);
        var parser = new PluginConfigPreParser();

        PluginConfigParseResult result = parser.parse(migrapheYaml);

        assertThat(result.plugins()).hasSize(1);
        PluginDeclaration plugin = result.plugins().get(0);
        assertThat(plugin.coordinate().groupId()).isEqualTo("com.github.alice");
        assertThat(plugin.coordinate().artifactId()).isEqualTo("migraphe-plugin-foo");
        assertThat(plugin.coordinate().version()).isEqualTo("v1.2.0");
        assertThat(plugin.repositoryRef()).contains("jitpack");
    }

    @Test
    void parseAcceptsMixedStringAndMapFormEntries() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                plugins:
                  - io.example:plain:1.0.0
                  - coordinate: com.github.alice:migraphe-plugin-foo:v1.2.0
                    repository: jitpack
                """);
        var parser = new PluginConfigPreParser();

        PluginConfigParseResult result = parser.parse(migrapheYaml);

        assertThat(result.plugins()).hasSize(2);
        assertThat(result.plugins().get(0).coordinate().artifactId()).isEqualTo("plain");
        assertThat(result.plugins().get(0).repositoryRef()).isEmpty();
        assertThat(result.plugins().get(1).repositoryRef()).contains("jitpack");
    }

    @Test
    void parseRejectsInvalidPluginEntryType() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                plugins:
                  - 42
                """);
        var parser = new PluginConfigPreParser();

        assertThatThrownBy(() -> parser.parse(migrapheYaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plugins[0]");
    }

    @Test
    void parseReturnsRepositoriesWhenSectionPresent() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                repositories:
                  - id: jitpack
                    url: https://jitpack.io
                  - id: internal
                    url: https://nexus.example.com/maven2
                """);
        var parser = new PluginConfigPreParser();

        PluginConfigParseResult result = parser.parse(migrapheYaml);

        assertThat(result.repositories()).hasSize(2);
        assertThat(result.repositories().get(0).id()).isEqualTo("jitpack");
        assertThat(result.repositories().get(0).url()).isEqualTo("https://jitpack.io");
        assertThat(result.repositories().get(1).id()).isEqualTo("internal");
    }

    @Test
    void parseReturnsEmptyRepositoriesWhenSectionAbsent() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                plugins:
                  - io.example:plain:1.0.0
                """);
        var parser = new PluginConfigPreParser();

        PluginConfigParseResult result = parser.parse(migrapheYaml);

        assertThat(result.repositories()).isEmpty();
    }

    @Test
    void parseRejectsRepositoryEntryThatIsNotMap() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                repositories:
                  - "not-a-map"
                """);
        var parser = new PluginConfigPreParser();

        assertThatThrownBy(() -> parser.parse(migrapheYaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repositories[0]");
    }

    @Test
    void parseRejectsRepositoryEntryMissingId() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                repositories:
                  - url: https://example.com
                """);
        var parser = new PluginConfigPreParser();

        assertThatThrownBy(() -> parser.parse(migrapheYaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repositories[0]")
                .hasMessageContaining("id");
    }

    @Test
    void parseRejectsRepositoriesValueNotAList() throws IOException {
        Path migrapheYaml = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                migrapheYaml,
                """
                repositories: "https://jitpack.io"
                """);
        var parser = new PluginConfigPreParser();

        assertThatThrownBy(() -> parser.parse(migrapheYaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repositories")
                .hasMessageContaining("list");
    }
}
