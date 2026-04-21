package io.github.kakusuke.migraphe.api.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class GeneratorOutputPluginTest {

    @Test
    void outputContextDefinitionAsDelegatesToResolver() {
        GeneratorDefinition definition =
                new GeneratorDefinition() {
                    @Override
                    public String type() {
                        return "test-type";
                    }
                };
        DefinitionResolver resolver =
                new DefinitionResolver() {
                    @Override
                    public <T extends GeneratorDefinition> T resolve(Class<T> klass) {
                        return klass.cast(definition);
                    }
                };
        Path outputDir = Path.of("/tmp/output");

        OutputContext context = new OutputContext(resolver, outputDir);

        assertThat(context.definitionAs(GeneratorDefinition.class)).isSameAs(definition);
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
                };
        DefinitionResolver resolver =
                new DefinitionResolver() {
                    @Override
                    public <T extends GeneratorDefinition> T resolve(Class<T> klass) {
                        return klass.cast(definition);
                    }
                };
        OutputContext context = new OutputContext(resolver, Path.of("/tmp/output"));

        assertThat(plugin.type()).isEqualTo("test-output");
        assertThat(plugin.canHandle(String.class)).isTrue();
        assertThat(plugin.canHandle(Integer.class)).isFalse();
        assertThat(plugin.definitionClass()).isEqualTo(GeneratorDefinition.class);

        plugin.output("data", context);
        assertThat(outputCalled.get()).isTrue();
    }

    @Test
    void outputContextAcceptsGeneratorDefinitionWithoutTarget() {
        GeneratorDefinition definition =
                new GeneratorDefinition() {
                    @Override
                    public String type() {
                        return "source-only-type";
                    }
                };
        DefinitionResolver resolver =
                new DefinitionResolver() {
                    @Override
                    public <T extends GeneratorDefinition> T resolve(Class<T> klass) {
                        return klass.cast(definition);
                    }
                };
        OutputContext context = new OutputContext(resolver, Path.of("/tmp/output"));

        assertThat(context.definitionAs(GeneratorDefinition.class).type())
                .isEqualTo("source-only-type");
    }
}
