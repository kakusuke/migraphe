package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;

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
}
