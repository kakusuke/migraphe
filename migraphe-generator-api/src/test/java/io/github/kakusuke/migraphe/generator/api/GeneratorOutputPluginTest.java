package io.github.kakusuke.migraphe.generator.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class GeneratorOutputPluginTest {

    @Test
    void outputContextHoldsDefinitionAndOutputDir() {
        GeneratorDefinition definition =
                new GeneratorDefinition() {
                    @Override
                    public String type() {
                        return "test-type";
                    }

                    @Override
                    public String target() {
                        return "test-target";
                    }
                };
        Path outputDir = Path.of("/tmp/output");

        OutputContext context = new OutputContext(definition, outputDir);

        assertThat(context.definition()).isSameAs(definition);
        assertThat(context.outputDir()).isEqualTo(outputDir);
    }

    @Test
    void generatorOutputPluginMethodsReturnExpectedValues() {
        AtomicBoolean outputCalled = new AtomicBoolean(false);
        GeneratorOutputPlugin plugin =
                new GeneratorOutputPlugin() {
                    @Override
                    public String type() {
                        return "test-output";
                    }

                    @Override
                    public boolean canHandle(Class<?> dataClass) {
                        return dataClass == String.class;
                    }

                    @Override
                    public Class<? extends GeneratorDefinition> definitionClass() {
                        return GeneratorDefinition.class;
                    }

                    @Override
                    public void output(Object data, OutputContext context) {
                        outputCalled.set(true);
                    }
                };

        GeneratorDefinition definition =
                new GeneratorDefinition() {
                    @Override
                    public String type() {
                        return "test-type";
                    }

                    @Override
                    public String target() {
                        return "test-target";
                    }
                };
        OutputContext context = new OutputContext(definition, Path.of("/tmp/output"));

        assertThat(plugin.type()).isEqualTo("test-output");
        assertThat(plugin.canHandle(String.class)).isTrue();
        assertThat(plugin.canHandle(Integer.class)).isFalse();
        assertThat(plugin.definitionClass()).isEqualTo(GeneratorDefinition.class);

        plugin.output("data", context);
        assertThat(outputCalled.get()).isTrue();
    }
}
