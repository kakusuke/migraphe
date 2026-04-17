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
 * GeneratorSourcePlugin / GeneratorOutputPlugin を使用してジェネレーターを実行する。
 *
 * <p>GeneratorRegistry からプラグインを取得し、設定に基づいてジェネレーターを生成・実行する。
 */
public final class GeneratorExecutor {

    private final GeneratorRegistry registry;

    public GeneratorExecutor(GeneratorRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * 複数のジェネレーター設定を実行する（SmallRyeConfig なし）。DefinitionResolver は GeneratorSection
     * からの簡易実装を使用するため、プラグイン固有の @ConfigMapping フィールドは解決できない。
     */
    public void executeAll(
            List<ProjectConfig.GeneratorSection> generators,
            Map<String, Environment> environments,
            @Nullable MigrationGraphView graph,
            Path baseDir,
            @Nullable String nameFilter) {
        executeAll(generators, environments, graph, null, null, baseDir, nameFilter);
    }

    /** 複数のジェネレーター設定を実行する（HistoryRepository 付き、SmallRyeConfig なし）。 */
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
     * 複数のジェネレーター設定を実行する（SmallRyeConfig 付き）。SmallRyeConfig が渡されている場合、 {@link
     * PropertiesDefinitionResolver} により、プラグイン固有の @ConfigMapping インターフェースを プラグイン側クラスローダーで再具現化できる。
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

    /** 単一のジェネレーター設定を実行する（SmallRyeConfig なし、後方互換）。 */
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

    /** 単一のジェネレーター設定を実行する（DefinitionResolver を明示的に指定）。 */
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
     * SmallRyeConfig が与えられない場合に使用される簡易 DefinitionResolver。 {@link GeneratorDefinition} の最小契約
     * ({@code type()}) だけを満たし、それ以外の型は解決できない。
     */
    private record GeneratorSectionFallbackResolver(ProjectConfig.GeneratorSection config)
            implements DefinitionResolver {

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
