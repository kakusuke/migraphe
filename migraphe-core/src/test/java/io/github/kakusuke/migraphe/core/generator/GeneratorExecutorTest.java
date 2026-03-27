package io.github.kakusuke.migraphe.core.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.core.config.ProjectConfig;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import io.github.kakusuke.migraphe.generator.api.Generator;
import io.github.kakusuke.migraphe.generator.api.GeneratorDefinition;
import io.github.kakusuke.migraphe.generator.api.GeneratorPlugin;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
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

        executor.executeAll(generators, environments, tempDir, "gen-a");

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

        executor.executeAll(generators, environments, tempDir, null);

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

        assertThatThrownBy(() -> executor.executeAll(generators, environments, tempDir, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
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
        public Optional<List<ExcludeSection>> excludes() {
            return Optional.empty();
        }
    }
}
