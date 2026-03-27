package io.github.kakusuke.migraphe.generator.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.environment.Environment;
import org.junit.jupiter.api.Test;

class GeneratorPluginTest {

    @Test
    void typeReturnsExpectedValue() {
        // given
        GeneratorPlugin plugin =
                new GeneratorPlugin() {
                    @Override
                    public String type() {
                        return "test-generator";
                    }

                    @Override
                    public Class<? extends GeneratorDefinition> definitionClass() {
                        return GeneratorDefinition.class;
                    }

                    @Override
                    public Generator createGenerator(
                            Environment environment, GeneratorDefinition definition) {
                        return outputDir -> {};
                    }
                };

        // when
        String type = plugin.type();

        // then
        assertThat(type).isEqualTo("test-generator");
    }
}
