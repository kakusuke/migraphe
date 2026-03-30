package io.github.kakusuke.migraphe.core.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.core.config.ProjectConfig;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import io.github.kakusuke.migraphe.generator.api.Generator;
import io.github.kakusuke.migraphe.generator.api.GeneratorDefinition;
import io.github.kakusuke.migraphe.generator.api.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.generator.api.GeneratorPlugin;
import io.github.kakusuke.migraphe.generator.api.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.generator.api.OutputContext;
import io.github.kakusuke.migraphe.generator.api.SourceContext;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratorExecutorTest {

    @TempDir Path tempDir;

    @Test
    void executeCallsGeneratorPluginAndGeneratesOutput() {
        var invoked = new AtomicBoolean(false);
        var registry = new GeneratorRegistry();
        registry.register(
                new StubGeneratorPlugin(
                        "test-type",
                        (env, def) ->
                                outputDir -> {
                                    invoked.set(true);
                                }));

        var executor = new GeneratorExecutor(registry);
        var config = new StubGeneratorSection("gen1", "test-type", "dev", "output");
        var environment = SimpleEnvironment.create(EnvironmentId.of("dev"), "dev");

        executor.execute(config, environment, tempDir);

        assertThat(invoked).isTrue();
    }

    @Test
    void executeThrowsForUnknownType() {
        var registry = new GeneratorRegistry();
        var executor = new GeneratorExecutor(registry);
        var config = new StubGeneratorSection("gen1", "unknown-type", "dev", "output");
        var environment = SimpleEnvironment.create(EnvironmentId.of("dev"), "dev");

        assertThatThrownBy(() -> executor.execute(config, environment, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown-type");
    }

    @Test
    void executeAllFiltersById() {
        var invokedTypes = new ArrayList<String>();
        var registry = new GeneratorRegistry();
        registry.register(
                new StubGeneratorPlugin(
                        "type-a",
                        (env, def) ->
                                outputDir -> {
                                    invokedTypes.add("type-a");
                                }));
        registry.register(
                new StubGeneratorPlugin(
                        "type-b",
                        (env, def) ->
                                outputDir -> {
                                    invokedTypes.add("type-b");
                                }));

        var executor = new GeneratorExecutor(registry);
        var generators =
                List.<ProjectConfig.GeneratorSection>of(
                        new StubGeneratorSection("gen-a", "type-a", "dev", "output-a"),
                        new StubGeneratorSection("gen-b", "type-b", "dev", "output-b"));
        var environments =
                Map.<String, Environment>of(
                        "dev", SimpleEnvironment.create(EnvironmentId.of("dev"), "dev"));

        executor.executeAll(generators, environments, null, tempDir, "gen-a");

        assertThat(invokedTypes).containsExactly("type-a");
    }

    @Test
    void executeAllRunsAllWhenNoFilter() {
        var invokedNames = new ArrayList<String>();
        var registry = new GeneratorRegistry();
        registry.register(
                new StubGeneratorPlugin(
                        "type-a",
                        (env, def) ->
                                outputDir -> {
                                    invokedNames.add("a");
                                }));
        registry.register(
                new StubGeneratorPlugin(
                        "type-b",
                        (env, def) ->
                                outputDir -> {
                                    invokedNames.add("b");
                                }));

        var executor = new GeneratorExecutor(registry);
        var generators =
                List.<ProjectConfig.GeneratorSection>of(
                        new StubGeneratorSection("gen-a", "type-a", "dev", "output-a"),
                        new StubGeneratorSection("gen-b", "type-b", "dev", "output-b"));
        var environments =
                Map.<String, Environment>of(
                        "dev", SimpleEnvironment.create(EnvironmentId.of("dev"), "dev"));

        executor.executeAll(generators, environments, null, tempDir, null);

        assertThat(invokedNames).containsExactly("a", "b");
    }

    @Test
    void executeAllThrowsForMissingEnvironment() {
        var registry = new GeneratorRegistry();
        registry.register(new StubGeneratorPlugin("type-a", (env, def) -> outputDir -> {}));

        var executor = new GeneratorExecutor(registry);
        var generators =
                List.<ProjectConfig.GeneratorSection>of(
                        new StubGeneratorSection("gen-a", "type-a", "nonexistent", "output"));
        Map<String, Environment> environments = Map.of();

        assertThatThrownBy(() -> executor.executeAll(generators, environments, null, tempDir, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }

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

        executor.executeWithSourceOutput(config, environments, null, tempDir);

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
                        () -> executor.executeWithSourceOutput(config, environments, null, tempDir))
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
                        () -> executor.executeWithSourceOutput(config, environments, null, tempDir))
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

    @FunctionalInterface
    private interface GeneratorFactory {
        Generator create(Environment environment, GeneratorDefinition definition);
    }

    private static class StubGeneratorPlugin implements GeneratorPlugin {
        private final String type;
        private final GeneratorFactory factory;

        StubGeneratorPlugin(String type, GeneratorFactory factory) {
            this.type = type;
            this.factory = factory;
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
            return factory.create(environment, definition);
        }
    }

    private record StubGeneratorSection(String name, String type, String target, String outputDir)
            implements ProjectConfig.GeneratorSection {

        @Override
        public SourceSection source() {
            return new SourceSection() {
                @Override
                public Optional<String> type() {
                    return Optional.empty();
                }

                @Override
                public Optional<String> target() {
                    return Optional.ofNullable(target);
                }
            };
        }

        @Override
        public Optional<List<ExcludeSection>> excludes() {
            return Optional.empty();
        }
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
