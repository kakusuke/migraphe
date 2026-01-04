package io.github.migraphe.cli.config;

import io.github.migraphe.api.graph.NodeId;
import io.github.migraphe.core.config.ConfigurationException;
import io.github.migraphe.core.config.ProjectConfig;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        return loadConfig(baseDir, Optional.empty());
    }

    /**
     * YAML ファイルから設定をロードして SmallRyeConfig を構築する。
     *
     * @param baseDir プロジェクトルートディレクトリ
     * @param envName 環境名 (オプション。指定された場合は environments/{envName}.yaml をロード)
     * @return SmallRyeConfig
     * @throws ConfigurationException 設定ファイルのロードに失敗した場合
     */
    public SmallRyeConfig loadConfig(Path baseDir, Optional<String> envName) {
        YamlFileScanner scanner = new YamlFileScanner();
        TaskIdGenerator idGenerator = new TaskIdGenerator();

        // 1. プロジェクト設定ファイル (migraphe.yaml) を発見
        Path projectConfigFile =
                scanner.findProjectConfig(baseDir)
                        .orElseThrow(
                                () ->
                                        new ConfigurationException(
                                                "Project config file not found: "
                                                        + baseDir.resolve("migraphe.yaml")));

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
        Optional<Path> envFile = envName.flatMap(env -> scanner.findEnvironmentFile(baseDir, env));

        if (envFile.isPresent()) {
            try {
                YamlConfigSource envSource =
                        new YamlConfigSource(envFile.get().toUri().toURL(), 500);
                // 環境ファイルと MultiFileYamlConfigSource を同時に追加
                builder.withSources(envSource, multiFileSource);
            } catch (IOException e) {
                throw new ConfigurationException(
                        "Failed to load environment file: " + envFile.get(), e);
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
     * tasks/ ディレクトリから個別の TaskConfig を読み込む。
     *
     * @param tasksDir tasks/ ディレクトリのパス
     * @return Path → SmallRyeConfig のマップ (各ファイルが個別の Config)
     * @throws ConfigurationException 設定ファイルのロードに失敗した場合
     */
    public Map<Path, SmallRyeConfig> loadTaskConfigs(Path tasksDir) {
        YamlFileScanner scanner = new YamlFileScanner();
        Map<Path, SmallRyeConfig> taskConfigs = new HashMap<>();

        // tasks/ ディレクトリ配下の全YAMLファイルをスキャン
        List<Path> taskFiles = scanner.scanTaskFiles(tasksDir.getParent());

        for (Path taskFile : taskFiles) {
            try {
                // 各タスクファイルを個別の Config として読み込む
                YamlConfigSource taskSource = new YamlConfigSource(taskFile.toUri().toURL());

                SmallRyeConfig taskConfig =
                        new SmallRyeConfigBuilder()
                                .withSources(taskSource)
                                .withMapping(io.github.migraphe.core.config.TaskConfig.class)
                                .build();

                taskConfigs.put(taskFile, taskConfig);

            } catch (IOException e) {
                throw new ConfigurationException("Failed to load task file: " + taskFile, e);
            }
        }

        return taskConfigs;
    }
}
