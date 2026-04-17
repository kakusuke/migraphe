package io.github.kakusuke.migraphe.output.json;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonOutputPluginTest {

    @TempDir Path tempDir;

    private final JsonOutputPlugin plugin = new JsonOutputPlugin();

    @Test
    void typeIsOutputJson() {
        assertThat(plugin.type()).isEqualTo("output-json");
    }

    @Test
    void canHandleAnyType() {
        assertThat(plugin.canHandle(String.class)).isTrue();
        assertThat(plugin.canHandle(Map.class)).isTrue();
        assertThat(plugin.canHandle(List.class)).isTrue();
    }

    @Test
    void outputPrintsJsonToStdout() {
        var definition =
                new GeneratorDefinition() {
                    @Override
                    public String type() {
                        return "output-json";
                    }
                };
        var context = new OutputContext(definition, tempDir);
        var data = Map.of("name", "test", "count", 42);

        var originalOut = System.out;
        var baos = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(baos));
            plugin.output(data, context);
        } finally {
            System.setOut(originalOut);
        }

        String output = baos.toString();
        assertThat(output).contains("\"name\"");
        assertThat(output).contains("\"test\"");
        assertThat(output).contains("42");
    }

    @Test
    void discoveredByServiceLoader() {
        var plugins = ServiceLoader.load(GeneratorOutputPlugin.class);
        var found =
                plugins.stream()
                        .map(ServiceLoader.Provider::get)
                        .filter(p -> "output-json".equals(p.type()))
                        .findFirst();
        assertThat(found).isPresent();
        assertThat(found.get()).isInstanceOf(JsonOutputPlugin.class);
    }
}
