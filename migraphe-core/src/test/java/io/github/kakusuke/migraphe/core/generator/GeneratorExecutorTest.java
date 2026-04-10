package io.github.kakusuke.migraphe.core.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;
import io.github.kakusuke.migraphe.api.generator.SourceContext;
import io.github.kakusuke.migraphe.core.config.ProjectConfig;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratorExecutorTest {

    @TempDir Path tempDir;

    @Test
    void executeWithSourceOutputExtractsDataAndOutputs() {
        var capturedData = new AtomicReference<Object>();
        var capturedOutputDir = new AtomicReference<Path>();
        var registry = new GeneratorRegistry();
        registry.registerSource(
                new GeneratorSourcePlugin<String>() {
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
                        return "extracted-data";
                    }
                });
        registry.registerOutput(
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
                        capturedData.set(data);
                        capturedOutputDir.set(context.outputDir());
                    }
                });

        var executor = new GeneratorExecutor(registry);
        var config =
                new StubGeneratorSectionWithSource(
                        "gen1", "test-output", "dev", "output", "test-source", null);
        var environments =
                Map.<String, Environment>of(
                        "dev", SimpleEnvironment.create(EnvironmentId.of("dev"), "dev"));

        executor.executeWithSourceOutput(config, environments, null, null, tempDir);

        assertThat(capturedData.get()).isEqualTo("extracted-data");
        assertThat(capturedOutputDir.get()).isEqualTo(tempDir.resolve("output"));
    }

    @Test
    void executeWithSourceOutputThrowsWhenSourceNotFound() {
        var registry = new GeneratorRegistry();
        registry.registerOutput(
                new GeneratorOutputPlugin() {
                    @Override
                    public String type() {
                        return "test-output";
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
                });

        var executor = new GeneratorExecutor(registry);
        var config =
                new StubGeneratorSectionWithSource(
                        "gen1", "test-output", "dev", "output", "missing-source", null);
        var environments =
                Map.<String, Environment>of(
                        "dev", SimpleEnvironment.create(EnvironmentId.of("dev"), "dev"));

        assertThatThrownBy(
                        () ->
                                executor.executeWithSourceOutput(
                                        config, environments, null, null, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing-source");
    }

    @Test
    void executeWithSourceOutputThrowsWhenOutputNotFound() {
        var registry = new GeneratorRegistry();
        registry.registerSource(
                new GeneratorSourcePlugin<String>() {
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
                        return "data";
                    }
                });

        var executor = new GeneratorExecutor(registry);
        var config =
                new StubGeneratorSectionWithSource(
                        "gen1", "missing-output", "dev", "output", "test-source", null);
        var environments =
                Map.<String, Environment>of(
                        "dev", SimpleEnvironment.create(EnvironmentId.of("dev"), "dev"));

        assertThatThrownBy(
                        () ->
                                executor.executeWithSourceOutput(
                                        config, environments, null, null, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing-output");
    }

    @Test
    void executeAllRoutesToSourceOutputFlowWhenSourceTypeIsSet() {
        var capturedData = new AtomicReference<Object>();
        var registry = new GeneratorRegistry();
        registry.registerSource(
                new GeneratorSourcePlugin<String>() {
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
                        return "extracted-data";
                    }
                });
        registry.registerOutput(
                new GeneratorOutputPlugin() {
                    @Override
                    public String type() {
                        return "test-output";
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
                    public void output(Object data, OutputContext context) {
                        capturedData.set(data);
                    }
                });

        var executor = new GeneratorExecutor(registry);
        var config =
                new StubGeneratorSectionWithSource(
                        "gen1", "test-output", "dev", "output", "test-source", null);
        var environments =
                Map.<String, Environment>of(
                        "dev", SimpleEnvironment.create(EnvironmentId.of("dev"), "dev"));

        executor.executeAll(List.of(config), environments, null, tempDir, null);

        assertThat(capturedData.get()).isEqualTo("extracted-data");
    }

    private record StubGeneratorSectionWithSource(
            String name,
            String type,
            String target,
            String outputDir,
            String sourceType,
            @Nullable String sourceTarget)
            implements ProjectConfig.GeneratorSection {

        @Override
        public SourceSection source() {
            return new SourceSection() {
                @Override
                public Optional<String> type() {
                    return Optional.ofNullable(sourceType);
                }

                @Override
                public Optional<String> target() {
                    return Optional.ofNullable(sourceTarget);
                }
            };
        }

        @Override
        public Optional<List<ExcludeSection>> excludes() {
            return Optional.empty();
        }
    }
}
