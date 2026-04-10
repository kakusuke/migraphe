package io.github.kakusuke.migraphe.core.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;
import io.github.kakusuke.migraphe.api.generator.SourceContext;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratorRegistryTest {

    @Test
    void loadFromClassLoaderDiscoversSourcePlugins() {
        var registry = new GeneratorRegistry();
        registry.loadFromClassLoader(Thread.currentThread().getContextClassLoader());

        assertThat(registry.findSourceByType("test-source")).isPresent();
    }

    @Test
    void loadFromClassLoaderDiscoversOutputPlugins() {
        var registry = new GeneratorRegistry();
        registry.loadFromClassLoader(Thread.currentThread().getContextClassLoader());

        assertThat(registry.findOutputByType("test-output")).isPresent();
    }

    @Test
    void registerSourceAndFindByType() {
        var registry = new GeneratorRegistry();
        GeneratorSourcePlugin<?> plugin = new StubGeneratorSourcePlugin("jdbc-schema");
        registry.registerSource(plugin);

        assertThat(registry.findSourceByType("jdbc-schema")).isPresent().get().isSameAs(plugin);
    }

    @Test
    void findSourceByTypeReturnsEmptyWhenNotFound() {
        var registry = new GeneratorRegistry();

        assertThat(registry.findSourceByType("unknown-source")).isEmpty();
    }

    @Test
    void registerOutputAndFindByType() {
        var registry = new GeneratorRegistry();
        GeneratorOutputPlugin plugin = new StubGeneratorOutputPlugin("markdown");
        registry.registerOutput(plugin);

        assertThat(registry.findOutputByType("markdown")).isPresent().get().isSameAs(plugin);
    }

    @Test
    void findOutputByTypeReturnsEmptyWhenNotFound() {
        var registry = new GeneratorRegistry();

        assertThat(registry.findOutputByType("unknown-output")).isEmpty();
    }

    @Test
    void loadFromDirectoryDoesNothingWhenDirectoryDoesNotExist(@TempDir Path tempDir) {
        var registry = new GeneratorRegistry();
        Path nonexistent = tempDir.resolve("nonexistent");

        assertThatCode(() -> registry.loadFromDirectory(nonexistent)).doesNotThrowAnyException();
    }

    private static class StubGeneratorSourcePlugin implements GeneratorSourcePlugin<String> {
        private final String type;

        StubGeneratorSourcePlugin(String type) {
            this.type = type;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public Class<String> dataClass() {
            return String.class;
        }

        @Override
        public String extract(SourceContext context) {
            return "";
        }
    }

    private static class StubGeneratorOutputPlugin implements GeneratorOutputPlugin {
        private final String type;

        StubGeneratorOutputPlugin(String type) {
            this.type = type;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public boolean canHandle(Class<?> dataClass) {
            return true;
        }

        @Override
        public Class<? extends GeneratorDefinition> definitionClass() {
            return GeneratorDefinition.class;
        }

        @Override
        public void output(Object data, OutputContext context) {}
    }
}
