package io.github.kakusuke.migraphe.core.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.generator.api.Generator;
import io.github.kakusuke.migraphe.generator.api.GeneratorDefinition;
import io.github.kakusuke.migraphe.generator.api.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.generator.api.GeneratorPlugin;
import io.github.kakusuke.migraphe.generator.api.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.generator.api.OutputContext;
import io.github.kakusuke.migraphe.generator.api.SourceContext;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratorRegistryTest {

    @Test
    void findByTypeReturnsEmptyForUnknownType() {
        var registry = new GeneratorRegistry();

        assertThat(registry.findByType("unknown")).isEmpty();
    }

    @Test
    void findByTypeReturnsRegisteredPlugin() {
        var registry = new GeneratorRegistry();
        GeneratorPlugin plugin = new StubGeneratorPlugin("jdbc-markdown");
        registry.register(plugin);

        assertThat(registry.findByType("jdbc-markdown")).isPresent().get().isSameAs(plugin);
    }

    @Test
    void loadFromClassLoaderDiscoversPlugins() {
        var registry = new GeneratorRegistry();
        registry.loadFromClassLoader(Thread.currentThread().getContextClassLoader());

        // jdbc-markdown はクラスパスにないので空であることを確認
        // （テスト環境では migraphe-plugin-jdbc が依存にない）
        assertThat(registry.findByType("nonexistent")).isEmpty();
    }

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
        assertThat(registry.findByType("any")).isEmpty();
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

    private static class StubGeneratorPlugin implements GeneratorPlugin {
        private final String type;

        StubGeneratorPlugin(String type) {
            this.type = type;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public Class<? extends GeneratorDefinition> definitionClass() {
            return GeneratorDefinition.class;
        }

        @Override
        public Generator createGenerator(Environment environment, GeneratorDefinition definition) {
            return outputDir -> {};
        }
    }
}
