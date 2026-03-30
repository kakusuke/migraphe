package io.github.kakusuke.migraphe.generator.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeneratorSourcePluginTest {

    @Test
    void sourceContextAllowsNullFields() {
        SourceContext context = new SourceContext(null, null);

        assertThat(context.environment()).isNull();
        assertThat(context.graph()).isNull();
    }

    @Test
    void generatorSourcePluginMethodsReturnExpectedValues() {
        GeneratorSourcePlugin<String> plugin =
                new GeneratorSourcePlugin<>() {
                    @Override
                    public String type() {
                        return "test-source";
                    }

                    @Override
                    public Class<String> dataClass() {
                        return String.class;
                    }

                    @Override
                    public String extract(SourceContext context) {
                        return "extracted";
                    }
                };

        assertThat(plugin.type()).isEqualTo("test-source");
        assertThat(plugin.dataClass()).isEqualTo(String.class);
        assertThat(plugin.extract(new SourceContext(null, null))).isEqualTo("extracted");
    }
}
