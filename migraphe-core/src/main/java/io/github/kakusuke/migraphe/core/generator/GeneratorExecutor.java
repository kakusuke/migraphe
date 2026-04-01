package io.github.kakusuke.migraphe.core.generator;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.config.ProjectConfig;
import io.github.kakusuke.migraphe.generator.api.Generator;
import io.github.kakusuke.migraphe.generator.api.GeneratorDefinition;
import io.github.kakusuke.migraphe.generator.api.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.generator.api.GeneratorPlugin;
import io.github.kakusuke.migraphe.generator.api.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.generator.api.OutputContext;
import io.github.kakusuke.migraphe.generator.api.SourceContext;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * GeneratorPlugin を使用してジェネレーターを実行する。
 *
 * <p>GeneratorRegistry からプラグインを取得し、設定に基づいてジェネレーターを生成・実行する。
 */
public final class GeneratorExecutor {

    private final GeneratorRegistry registry;

    public GeneratorExecutor(GeneratorRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * 単一のジェネレーター設定を実行する。
     *
     * @param config ジェネレーター設定
     * @param environment 実行環境
     * @param baseDir ベースディレクトリ
     * @throws IllegalArgumentException プラグインが見つからない場合
     */
    public void execute(
            ProjectConfig.GeneratorSection config, Environment environment, Path baseDir) {
        GeneratorPlugin plugin =
                registry.findByType(config.type())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Generator plugin not found for type: "
                                                        + config.type()));

        GeneratorDefinition definition = new GeneratorSectionAdapter(config);
        Generator generator = plugin.createGenerator(environment, definition);
        generator.generate(baseDir.resolve(config.outputDir()));
    }

    /**
     * 複数のジェネレーター設定を実行する。nameFilter が指定されている場合、一致するもののみ実行する。
     *
     * @param generators ジェネレーター設定リスト
     * @param environments 環境マップ（ターゲットID → Environment）
     * @param baseDir ベースディレクトリ
     * @param nameFilter 名前フィルター（null の場合はすべて実行）
     * @throws IllegalArgumentException 環境が見つからない場合
     */
    public void executeAll(
            List<ProjectConfig.GeneratorSection> generators,
            Map<String, Environment> environments,
            @Nullable MigrationGraphView graph,
            Path baseDir,
            @Nullable String nameFilter) {
        executeAll(generators, environments, graph, null, baseDir, nameFilter);
    }

    /**
     * 複数のジェネレーター設定を実行する（HistoryRepository 付き）。
     *
     * @param generators ジェネレーター設定リスト
     * @param environments 環境マップ
     * @param graph マイグレーショングラフ
     * @param historyRepository 履歴リポジトリ
     * @param baseDir ベースディレクトリ
     * @param nameFilter 名前フィルター
     */
    public void executeAll(
            List<ProjectConfig.GeneratorSection> generators,
            Map<String, Environment> environments,
            @Nullable MigrationGraphView graph,
            @Nullable HistoryRepository historyRepository,
            Path baseDir,
            @Nullable String nameFilter) {
        for (ProjectConfig.GeneratorSection config : generators) {
            if (nameFilter != null && !nameFilter.equals(config.name())) {
                continue;
            }
            if (config.source().type().isPresent()) {
                executeWithSourceOutput(config, environments, graph, historyRepository, baseDir);
            } else {
                Environment environment = environments.get(config.target());
                if (environment == null) {
                    throw new IllegalArgumentException(
                            "Environment not found for target: " + config.target());
                }
                execute(config, environment, baseDir);
            }
        }
    }

    /**
     * 新しいソース/アウトプットフローで単一のジェネレーター設定を実行する。
     *
     * @param config ジェネレーター設定
     * @param environments 環境マップ
     * @param graph マイグレーショングラフ（null可）
     * @param baseDir ベースディレクトリ
     */
    public void executeWithSourceOutput(
            ProjectConfig.GeneratorSection config,
            Map<String, Environment> environments,
            @Nullable MigrationGraphView graph,
            @Nullable HistoryRepository historyRepository,
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
                new OutputContext(
                        new GeneratorSectionAdapter(config), baseDir.resolve(config.outputDir()));
        outputPlugin.output(data, outputContext);
    }

    /** GeneratorSection を GeneratorDefinition に適合させるアダプター。 */
    private record GeneratorSectionAdapter(ProjectConfig.GeneratorSection config)
            implements GeneratorDefinition {

        @Override
        public String type() {
            return config.type();
        }

        @Override
        public String target() {
            return config.target();
        }
    }
}
