package io.github.kakusuke.migraphe.core.generator;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.generator.DefinitionResolver;
import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;
import io.github.kakusuke.migraphe.api.generator.SourceContext;
import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.config.ProjectConfig;
import io.smallrye.config.SmallRyeConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Drives generator execution by combining {@link GeneratorSourcePlugin source} and {@link
 * GeneratorOutputPlugin output} plugins.
 *
 * <p>For each configured generator it resolves the source plugin by its {@code source.type},
 * extracts a typed data object using a {@link SourceContext} assembled from the available
 * environment, migration graph, and history repository, then resolves the output plugin by the
 * generator's {@code type} and hands it the data together with an {@link OutputContext}. The
 * plugins themselves are obtained from the supplied {@link GeneratorRegistry}.
 *
 * <p>Several overloads exist that differ only in which optional inputs they carry. When a {@link
 * SmallRyeConfig} is provided, plugin-specific {@code @ConfigMapping} definitions can be
 * re-materialized through {@link PropertiesDefinitionResolver}; otherwise a minimal fallback
 * resolver is used that satisfies only the base {@link GeneratorDefinition} contract.
 */
public final class GeneratorExecutor {

    private final GeneratorRegistry registry;

    /**
     * Creates an executor that resolves plugins from the given registry.
     *
     * @param registry the registry holding the available source and output plugins
     * @throws NullPointerException if {@code registry} is {@code null}
     */
    public GeneratorExecutor(GeneratorRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * Runs all matching generators without a history repository or {@link SmallRyeConfig}.
     *
     * <p>Because no {@link SmallRyeConfig} is supplied, definitions are resolved by a minimal
     * fallback that exposes only the generator's {@code type}; plugin-specific
     * {@code @ConfigMapping} fields cannot be resolved.
     *
     * @param generators the generator configuration sections to run
     * @param environments the available environments, keyed by target id, for source plugins to
     *     connect to
     * @param graph a read-only view of the migration graph, or {@code null} if not applicable
     * @param baseDir the project base directory against which each generator's output directory is
     *     resolved
     * @param nameFilter when non-{@code null}, only the generator whose {@code name} equals this
     *     value is run; otherwise every generator is run
     */
    public void executeAll(
            List<ProjectConfig.GeneratorSection> generators,
            Map<String, Environment> environments,
            @Nullable MigrationGraphView graph,
            Path baseDir,
            @Nullable String nameFilter) {
        executeAll(generators, environments, graph, null, null, baseDir, nameFilter);
    }

    /**
     * Runs all matching generators with a history repository but no {@link SmallRyeConfig}.
     *
     * @param generators the generator configuration sections to run
     * @param environments the available environments, keyed by target id, for source plugins to
     *     connect to
     * @param graph a read-only view of the migration graph, or {@code null} if not applicable
     * @param historyRepository the execution-history repository, or {@code null} if not applicable
     * @param baseDir the project base directory against which each generator's output directory is
     *     resolved
     * @param nameFilter when non-{@code null}, only the generator whose {@code name} equals this
     *     value is run; otherwise every generator is run
     */
    public void executeAll(
            List<ProjectConfig.GeneratorSection> generators,
            Map<String, Environment> environments,
            @Nullable MigrationGraphView graph,
            @Nullable HistoryRepository historyRepository,
            Path baseDir,
            @Nullable String nameFilter) {
        executeAll(generators, environments, graph, historyRepository, null, baseDir, nameFilter);
    }

    /**
     * Runs all matching generators, optionally using a {@link SmallRyeConfig} for typed
     * definitions.
     *
     * <p>When {@code projectConfig} is non-{@code null}, each generator's plugin-specific
     * {@code @ConfigMapping} interface is re-materialized on the plugin's class loader through
     * {@link PropertiesDefinitionResolver}; otherwise a minimal fallback resolver is used.
     *
     * @param generators the generator configuration sections to run
     * @param environments the available environments, keyed by target id, for source plugins to
     *     connect to
     * @param graph a read-only view of the migration graph, or {@code null} if not applicable
     * @param historyRepository the execution-history repository, or {@code null} if not applicable
     * @param projectConfig the parsed project configuration enabling typed definition resolution,
     *     or {@code null} to fall back to the base-contract resolver
     * @param baseDir the project base directory against which each generator's output directory is
     *     resolved
     * @param nameFilter when non-{@code null}, only the generator whose {@code name} equals this
     *     value is run; otherwise every generator is run
     */
    public void executeAll(
            List<ProjectConfig.GeneratorSection> generators,
            Map<String, Environment> environments,
            @Nullable MigrationGraphView graph,
            @Nullable HistoryRepository historyRepository,
            @Nullable SmallRyeConfig projectConfig,
            Path baseDir,
            @Nullable String nameFilter) {
        for (int index = 0; index < generators.size(); index++) {
            ProjectConfig.GeneratorSection config = generators.get(index);
            if (nameFilter != null && !nameFilter.equals(config.name())) {
                continue;
            }
            DefinitionResolver resolver = resolverFor(config, index, projectConfig);
            executeWithSourceOutput(
                    config, environments, graph, historyRepository, resolver, baseDir);
        }
    }

    /**
     * Runs a single generator with the minimal fallback definition resolver.
     *
     * <p>Convenience overload that uses a resolver exposing only the generator's {@code type};
     * plugin-specific {@code @ConfigMapping} definitions are not available.
     *
     * @param config the generator configuration section to run
     * @param environments the available environments, keyed by target id
     * @param graph a read-only view of the migration graph, or {@code null} if not applicable
     * @param historyRepository the execution-history repository, or {@code null} if not applicable
     * @param baseDir the project base directory against which the output directory is resolved
     */
    public void executeWithSourceOutput(
            ProjectConfig.GeneratorSection config,
            Map<String, Environment> environments,
            @Nullable MigrationGraphView graph,
            @Nullable HistoryRepository historyRepository,
            Path baseDir) {
        executeWithSourceOutput(
                config,
                environments,
                graph,
                historyRepository,
                new GeneratorSectionFallbackResolver(config),
                baseDir);
    }

    /**
     * Runs a single generator using an explicitly supplied definition resolver.
     *
     * <p>Resolves the source plugin from {@code config.source().type()}, extracts data through a
     * {@link SourceContext} built from the target environment (looked up by {@code
     * config.source().target()}), the graph, and the history repository, then resolves the output
     * plugin from {@code config.type()} and renders the data into {@code
     * baseDir.resolve(config.outputDir())} via an {@link OutputContext} carrying {@code resolver}.
     *
     * @param config the generator configuration section to run
     * @param environments the available environments, keyed by target id
     * @param graph a read-only view of the migration graph, or {@code null} if not applicable
     * @param historyRepository the execution-history repository, or {@code null} if not applicable
     * @param resolver the resolver used to materialize the output plugin's typed definition
     * @param baseDir the project base directory against which the output directory is resolved
     * @throws IllegalArgumentException if {@code source.type} is absent, or if no source plugin
     *     matches {@code source.type}, or if no output plugin matches {@code config.type()}
     */
    public void executeWithSourceOutput(
            ProjectConfig.GeneratorSection config,
            Map<String, Environment> environments,
            @Nullable MigrationGraphView graph,
            @Nullable HistoryRepository historyRepository,
            DefinitionResolver resolver,
            Path baseDir) {
        String sourceType =
                config.source()
                        .type()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "source.type is required for source/output flow"));
        GeneratorSourcePlugin<?> sourcePlugin =
                registry.findSourceByType(sourceType)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Generator source plugin not found for type: "
                                                        + sourceType));
        Environment environment = config.source().target().map(environments::get).orElse(null);
        SourceContext sourceContext = new SourceContext(environment, graph, historyRepository);
        Object data = sourcePlugin.extract(sourceContext);

        GeneratorOutputPlugin outputPlugin =
                registry.findOutputByType(config.type())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Generator output plugin not found for type: "
                                                        + config.type()));
        OutputContext outputContext =
                new OutputContext(resolver, baseDir.resolve(config.outputDir()));
        outputPlugin.output(data, outputContext);
    }

    private static DefinitionResolver resolverFor(
            ProjectConfig.GeneratorSection config,
            int index,
            @Nullable SmallRyeConfig projectConfig) {
        if (projectConfig != null) {
            return new PropertiesDefinitionResolver(projectConfig, "generators[" + index + "]");
        }
        return new GeneratorSectionFallbackResolver(config);
    }

    /**
     * Minimal {@link DefinitionResolver} used when no {@link SmallRyeConfig} is available.
     *
     * <p>It satisfies only the base {@link GeneratorDefinition} contract by exposing the section's
     * {@code type()}; any request for a plugin-specific subtype is rejected.
     *
     * @param config the generator section whose {@code type} backs the resolved definition
     */
    private record GeneratorSectionFallbackResolver(ProjectConfig.GeneratorSection config)
            implements DefinitionResolver {

        /**
         * {@inheritDoc}
         *
         * <p>Only {@link GeneratorDefinition} itself is supported, returning a definition backed by
         * the section's {@code type()}; any other subtype is rejected.
         *
         * @throws UnsupportedOperationException if {@code klass} is a {@link GeneratorDefinition}
         *     subtype other than {@code GeneratorDefinition} itself
         */
        @Override
        public <T extends GeneratorDefinition> T resolve(Class<T> klass) {
            if (klass.equals(GeneratorDefinition.class)) {
                GeneratorDefinition def = config::type;
                return klass.cast(def);
            }
            throw new UnsupportedOperationException(
                    "Cannot resolve "
                            + klass.getName()
                            + " without SmallRyeConfig. "
                            + "Pass SmallRyeConfig to GeneratorExecutor.executeAll for typed"
                            + " @ConfigMapping resolution.");
        }
    }
}
