package io.github.kakusuke.migraphe.cli.config;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.core.config.ConfigurationException;
import io.github.kakusuke.migraphe.core.config.ProjectConfig;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

/** YAML ファイルから設定を読み込み、SmallRyeConfig を構築するローダー。 */
public class ConfigLoader {

    /**
     * YAML ファイルから設定をロードして SmallRyeConfig を構築する（環境指定なし）。
     *
     * @param baseDir プロジェクトルートディレクトリ
     * @return SmallRyeConfig
     * @throws ConfigurationException 設定ファイルのロードに失敗した場合
     */
    public SmallRyeConfig load(Path baseDir) {
        return loadConfig(baseDir, null);
    }

    /**
     * YAML ファイルから設定をロードして SmallRyeConfig を構築する。
     *
     * @param baseDir プロジェクトルートディレクトリ
     * @param envName 環境名 (null の場合は環境ファイルをロードしない)
     * @return SmallRyeConfig
     * @throws ConfigurationException 設定ファイルのロードに失敗した場合
     */
    public SmallRyeConfig loadConfig(Path baseDir, @Nullable String envName) {
        YamlFileScanner scanner = new YamlFileScanner();
        TaskIdGenerator idGenerator = new TaskIdGenerator();

        // 1. プロジェクト設定ファイル (migraphe.yaml) を発見
        Path projectConfigFile = scanner.findProjectConfig(baseDir);
        if (projectConfigFile == null) {
            throw new ConfigurationException(
                    "Project config file not found: " + baseDir.resolve("migraphe.yaml"));
        }

        // 2. ターゲットファイル (targets/*.yaml) をスキャン
        List<Path> targetFiles = scanner.scanTargetFiles(baseDir);

        // 3. タスクファイル (tasks/**/*.yaml) をスキャンして Task ID を生成
        List<Path> taskFilePaths = scanner.scanTaskFiles(baseDir);
        Map<NodeId, Path> taskFiles = new HashMap<>();
        for (Path taskFile : taskFilePaths) {
            NodeId taskId = idGenerator.generateTaskId(baseDir, taskFile);
            taskFiles.put(taskId, taskFile);
        }

        // 4. MultiFileYamlConfigSource を構築
        MultiFileYamlConfigSource multiFileSource =
                new MultiFileYamlConfigSource(projectConfigFile, targetFiles, taskFiles);

        // 5. SmallRyeConfigBuilder を構築
        // 注: ProjectConfig のみ withMapping を使用。
        // TargetConfig と TaskConfig は動的なプレフィックスを持つため、
        // プログラマティックに取得する必要がある。
        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder();

        // 6. 環境ファイルがあればロード (最優先 - ordinal 500)
        Path envFile = envName != null ? scanner.findEnvironmentFile(baseDir, envName) : null;

        if (envFile != null) {
            try {
                YamlConfigSource envSource = new YamlConfigSource(envFile.toUri().toURL(), 500);
                // 環境ファイルと MultiFileYamlConfigSource を同時に追加
                builder.withSources(envSource, multiFileSource);
            } catch (IOException e) {
                throw new ConfigurationException("Failed to load environment file: " + envFile, e);
            }
        } else {
            // 環境ファイルがない場合は MultiFileYamlConfigSource のみ
            builder.withSources(multiFileSource);
        }

        // 7. マッピングとバリデーション設定
        builder.withMapping(ProjectConfig.class).withValidateUnknown(false); // マッピングされていないプロパティを許可

        return builder.build();
    }

    /**
     * tasks/ ディレクトリから TaskDefinition を読み込む。
     *
     * <p>各タスクファイルの target フィールドからプラグインを特定し、 プラグイン固有の TaskDefinition サブタイプにマッピングする。
     *
     * @param baseDir プロジェクトのベースディレクトリ
     * @param mainConfig メイン設定（ターゲット情報を含む）
     * @param pluginRegistry プラグインレジストリ
     * @return NodeId → TaskDefinition のマップ
     * @throws ConfigurationException 設定ファイルのロードに失敗した場合
     */
    public Map<NodeId, TaskDefinition<?>> loadTaskDefinitions(
            Path baseDir, SmallRyeConfig mainConfig, PluginRegistry pluginRegistry) {

        YamlFileScanner scanner = new YamlFileScanner();
        TaskIdGenerator idGenerator = new TaskIdGenerator();
        Map<NodeId, TaskDefinition<?>> taskDefinitions = new LinkedHashMap<>();

        // tasks/ ディレクトリ配下の全YAMLファイルをスキャン
        List<Path> taskFiles = scanner.scanTaskFiles(baseDir);

        for (Path taskFile : taskFiles) {
            NodeId nodeId = idGenerator.generateTaskId(baseDir, taskFile);
            TaskDefinition<?> taskDef = loadTaskDefinition(taskFile, mainConfig, pluginRegistry);
            taskDefinitions.put(nodeId, taskDef);
        }

        return taskDefinitions;
    }

    /**
     * targets/ ディレクトリから EnvironmentDefinition を読み込む。
     *
     * <p>各ターゲットの type フィールドからプラグインを特定し、 プラグイン固有の EnvironmentDefinition サブタイプにマッピングする。
     *
     * @param mainConfig メイン設定（ターゲット情報を含む）
     * @param pluginRegistry プラグインレジストリ
     * @return targetId → EnvironmentDefinition のマップ
     * @throws ConfigurationException 設定ファイルのロードに失敗した場合
     */
    public Map<String, EnvironmentDefinition> loadEnvironmentDefinitions(
            SmallRyeConfig mainConfig, PluginRegistry pluginRegistry) {

        Map<String, EnvironmentDefinition> environmentDefinitions = new LinkedHashMap<>();

        // 1. target.* プレフィックスを持つプロパティからターゲットIDを抽出
        Set<String> targetIds = extractTargetIds(mainConfig);

        for (String targetId : targetIds) {
            EnvironmentDefinition envDef =
                    loadEnvironmentDefinition(targetId, mainConfig, pluginRegistry);
            environmentDefinitions.put(targetId, envDef);
        }

        return environmentDefinitions;
    }

    /**
     * 単一のターゲットから EnvironmentDefinition を読み込む。
     *
     * @param targetId ターゲットID
     * @param mainConfig メイン設定
     * @param pluginRegistry プラグインレジストリ
     * @return EnvironmentDefinition
     * @throws ConfigurationException 設定ファイルのロードに失敗した場合
     */
    public EnvironmentDefinition loadEnvironmentDefinition(
            String targetId, SmallRyeConfig mainConfig, PluginRegistry pluginRegistry) {

        String prefix = "target." + targetId + ".";

        // 1. type を取得
        String type = mainConfig.getValue(prefix + "type", String.class);
        if (type == null) {
            throw new ConfigurationException("Target type not found for target: " + targetId);
        }

        // 2. プラグインを取得
        MigraphePlugin<?> plugin = pluginRegistry.getRequiredPlugin(type);

        // 3. プラグインの EnvironmentDefinition クラスでマッピング
        // プレフィックス付きで EnvironmentDefinition を構築
        SmallRyeConfig envConfig =
                new SmallRyeConfigBuilder()
                        .withSources(new PrefixedConfigSource(mainConfig, prefix))
                        .withMapping(plugin.environmentDefinitionClass())
                        .withValidateUnknown(false)
                        .build();

        return envConfig.getConfigMapping(plugin.environmentDefinitionClass());
    }

    /**
     * 設定からターゲットIDを抽出する。
     *
     * @param config SmallRyeConfig
     * @return ターゲットIDのセット
     */
    private Set<String> extractTargetIds(SmallRyeConfig config) {
        Set<String> targetIds = new HashSet<>();

        StreamSupport.stream(config.getPropertyNames().spliterator(), false)
                .filter(name -> name.startsWith("target."))
                .map(name -> name.substring("target.".length()))
                .map(
                        name -> {
                            int dotIndex = name.indexOf('.');
                            return dotIndex >= 0 ? name.substring(0, dotIndex) : name;
                        })
                .forEach(targetIds::add);

        return targetIds;
    }

    /**
     * 単一のタスクファイルから TaskDefinition を読み込む。
     *
     * @param taskFile タスクファイルのパス
     * @param mainConfig メイン設定（ターゲット情報を含む）
     * @param pluginRegistry プラグインレジストリ
     * @return TaskDefinition
     * @throws ConfigurationException 設定ファイルのロードに失敗した場合
     */
    public TaskDefinition<?> loadTaskDefinition(
            Path taskFile, SmallRyeConfig mainConfig, PluginRegistry pluginRegistry) {

        try {
            YamlConfigSource taskSource = new YamlConfigSource(taskFile.toUri().toURL());

            // 1. まず target フィールドだけを読み取る
            SmallRyeConfig targetOnlyConfig =
                    new SmallRyeConfigBuilder()
                            .withSources(taskSource)
                            .withMapping(TaskTargetOnly.class)
                            .withValidateUnknown(false)
                            .build();

            TaskTargetOnly targetOnly = targetOnlyConfig.getConfigMapping(TaskTargetOnly.class);
            String targetId = targetOnly.target();

            // 2. target から type を取得
            String type = mainConfig.getValue("target." + targetId + ".type", String.class);
            if (type == null) {
                throw new ConfigurationException(
                        "Target type not found for target: " + targetId + " in file: " + taskFile);
            }

            // 3. プラグインを取得
            MigraphePlugin<?> plugin = pluginRegistry.getRequiredPlugin(type);

            // 4. プラグインの TaskDefinition クラスでマッピング
            // 注: YamlConfigSource を再作成（SmallRyeConfig はソースを使い切る）
            YamlConfigSource taskSource2 = new YamlConfigSource(taskFile.toUri().toURL());
            SmallRyeConfig taskConfig =
                    new SmallRyeConfigBuilder()
                            .withSources(taskSource2)
                            .withMapping(plugin.taskDefinitionClass())
                            .withValidateUnknown(false)
                            .build();

            return taskConfig.getConfigMapping(plugin.taskDefinitionClass());

        } catch (IOException e) {
            throw new ConfigurationException("Failed to load task file: " + taskFile, e);
        }
    }

    /** target フィールドのみを読み取るための最小インターフェース。 */
    @ConfigMapping(prefix = "")
    interface TaskTargetOnly {
        String target();
    }
}
