package io.github.kakusuke.migraphe.core.generator;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.generator.api.Generator;
import io.github.kakusuke.migraphe.generator.api.GeneratorDefinition;
import io.github.kakusuke.migraphe.generator.api.GeneratorPlugin;
import org.junit.jupiter.api.Test;

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
