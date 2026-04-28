package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginResolverTest {

    @TempDir Path tempDir;

    @Test
    void shouldReturnNullWhenNoPluginsConfigured() throws IOException {
        Files.writeString(
                tempDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test
                """);
        var resolver = new PluginResolver();

        URLClassLoader result = resolver.resolve(tempDir);

        assertThat(result).isNull();
    }

    @Test
    void shouldThrowWhenLockfileMissingButPluginsDeclared() throws IOException {
        Files.writeString(
                tempDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test
                plugins:
                  - io.example:plugin-a:1.0
                """);
        var resolver = new PluginResolver();

        assertThatThrownBy(() -> resolver.resolve(tempDir))
                .isInstanceOf(LockFileNotFoundException.class)
                .hasMessageContaining("migraphe.lock.yaml")
                .hasMessageContaining("migraphe pin");
    }

    @Test
    void shouldThrowWhenLockfileIsOutOfSyncWithYaml() throws IOException {
        Files.writeString(
                tempDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test
                plugins:
                  - io.example:plugin-a:1.0
                """);
        Files.writeString(
                tempDir.resolve("migraphe.lock.yaml"),
                """
                lockfile-version: 1
                plugins: []
                """);
        var resolver = new PluginResolver();

        assertThatThrownBy(() -> resolver.resolve(tempDir))
                .isInstanceOf(LockOutOfSyncException.class)
                .hasMessageContaining("io.example:plugin-a")
                .hasMessageContaining("migraphe pin");
    }
}
