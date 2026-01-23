package io.github.kakusuke.migraphe.cli.config;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** 設定ファイルをオフラインで検証するバリデーター。 DB接続なしで全エラーを蓄積して一括表示。 */
public class ConfigValidator {

    private final PluginRegistry pluginRegistry;

    public ConfigValidator(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    /** 検証結果を表す record。 */
    public record ValidationOutput(List<String> errors) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    /**
     * プロジェクトの設定ファイルを検証する。
     *
     * @param baseDir プロジェクトのベースディレクトリ
     * @return 検証結果
     */
    public ValidationOutput validate(Path baseDir) {
        List<String> errors = new ArrayList<>();
        YamlFileScanner scanner = new YamlFileScanner();
        TaskIdGenerator idGenerator = new TaskIdGenerator();

        // 1. migraphe.yaml 存在確認
        Path projectConfigFile = scanner.findProjectConfig(baseDir);
        if (projectConfigFile == null) {
            errors.add("migraphe.yaml: Project configuration file not found");
            // migraphe.yaml がなければ他の検証は意味がないので終了
            return new ValidationOutput(errors);
        }

        // 2. targets/*.yaml を個別ロード
        List<Path> targetFiles = scanner.scanTargetFiles(baseDir);
        Set<String> validTargetIds = new HashSet<>();
        Map<String, String> targetTypes = new HashMap<>();

        for (Path targetFile : targetFiles) {
            String targetId = targetFile.getFileName().toString().replaceAll("\\.yaml$", "");
            List<String> targetErrors = validateTargetFile(targetFile, targetId);
            if (targetErrors.isEmpty()) {
                validTargetIds.add(targetId);
                // type を取得
                String type = getTargetType(targetFile);
                if (type != null) {
                    targetTypes.put(targetId, type);
                }
            } else {
                errors.addAll(targetErrors);
            }
        }

        // 3. tasks/**/*.yaml を個別ロード
        List<Path> taskFiles = scanner.scanTaskFiles(baseDir);
        Map<NodeId, TaskInfo> validTasks = new HashMap<>();

        for (Path taskFile : taskFiles) {
            NodeId taskId = idGenerator.generateTaskId(baseDir, taskFile);
            List<String> taskErrors =
                    validateTaskFile(taskFile, taskId, validTargetIds, targetTypes);
            if (taskErrors.isEmpty()) {
                TaskInfo taskInfo = extractTaskInfo(taskFile);
                if (taskInfo != null) {
                    validTasks.put(taskId, taskInfo);
                }
            } else {
                errors.addAll(taskErrors);
            }
        }

        // 4. dependencies 参照先存在確認
        for (Map.Entry<NodeId, TaskInfo> entry : validTasks.entrySet()) {
            NodeId taskId = entry.getKey();
            TaskInfo taskInfo = entry.getValue();

            for (String depId : taskInfo.dependencies()) {
                NodeId depNodeId = NodeId.of(depId);
                if (!validTasks.containsKey(depNodeId)) {
                    errors.add(
                            "tasks/"
                                    + taskId.value()
                                    + ".yaml: Dependency '"
                                    + depId
                                    + "' not found");
                }
            }
        }

        // 5. サイクル検出（DFS）
        List<String> cycleErrors = detectCycles(validTasks);
        errors.addAll(cycleErrors);

        return new ValidationOutput(errors);
    }

    /** タスク情報を保持する内部クラス。 */
    private record TaskInfo(String target, List<String> dependencies) {}

    /** ターゲットファイルの検証。 */
    private List<String> validateTargetFile(Path targetFile, String targetId) {
        List<String> errors = new ArrayList<>();
        String relativePath = "targets/" + targetId + ".yaml";

        try {
            YamlConfigSource source = new YamlConfigSource(targetFile.toUri().toURL());
            Map<String, String> props = source.getProperties();

            // type が必須
            if (!props.containsKey("type") || props.get("type") == null) {
                errors.add(relativePath + ": Missing required property 'type'");
            } else {
                // type が登録済みプラグインか確認
                String type = props.get("type");
                if (!pluginRegistry.hasPlugin(type)) {
                    errors.add(relativePath + ": Unknown plugin type '" + type + "'");
                }
            }
        } catch (IOException e) {
            errors.add(relativePath + ": Failed to load - " + e.getMessage());
        } catch (Exception e) {
            errors.add(relativePath + ": Invalid YAML - " + e.getMessage());
        }

        return errors;
    }

    /** ターゲットファイルから type を取得。 */
    private @Nullable String getTargetType(Path targetFile) {
        try {
            YamlConfigSource source = new YamlConfigSource(targetFile.toUri().toURL());
            return source.getProperties().get("type");
        } catch (Exception e) {
            return null;
        }
    }

    /** タスクファイルの検証。 */
    private List<String> validateTaskFile(
            Path taskFile,
            NodeId taskId,
            Set<String> validTargetIds,
            Map<String, String> targetTypes) {
        List<String> errors = new ArrayList<>();
        String relativePath = "tasks/" + taskId.value() + ".yaml";

        try {
            YamlConfigSource source = new YamlConfigSource(taskFile.toUri().toURL());
            Map<String, String> props = source.getProperties();

            // name が必須
            if (!props.containsKey("name") || props.get("name") == null) {
                errors.add(relativePath + ": Missing required property 'name'");
            }

            // target が必須
            String target = props.get("target");
            if (target == null) {
                errors.add(relativePath + ": Missing required property 'target'");
            } else if (!validTargetIds.contains(target)) {
                errors.add(relativePath + ": Target '" + target + "' not found");
            } else {
                // target の type でプラグイン固有の検証を試みる
                String type = targetTypes.get(target);
                if (type != null) {
                    List<String> pluginErrors = validateWithPlugin(taskFile, type, relativePath);
                    errors.addAll(pluginErrors);
                }
            }
        } catch (IOException e) {
            errors.add(relativePath + ": Failed to load - " + e.getMessage());
        } catch (Exception e) {
            errors.add(relativePath + ": Invalid YAML - " + e.getMessage());
        }

        return errors;
    }

    /** プラグイン固有の検証を実行。 */
    private List<String> validateWithPlugin(Path taskFile, String type, String relativePath) {
        List<String> errors = new ArrayList<>();

        try {
            var plugin = pluginRegistry.getPlugin(type);
            if (plugin == null) {
                return errors;
            }

            Class<?> taskDefClass = plugin.taskDefinitionClass();

            // SmallRye Config でマッピングを試みる
            YamlConfigSource source = new YamlConfigSource(taskFile.toUri().toURL());
            SmallRyeConfig config =
                    new SmallRyeConfigBuilder()
                            .withSources(source)
                            .withMapping(taskDefClass)
                            .withValidateUnknown(false)
                            .build();

            // マッピングを取得（例外が発生すれば必須フィールド欠落）
            config.getConfigMapping(taskDefClass);

        } catch (io.smallrye.config.ConfigValidationException e) {
            // SmallRye の検証エラーからメッセージを抽出
            for (String problem : extractValidationProblems(e)) {
                errors.add(relativePath + ": " + problem);
            }
        } catch (Exception e) {
            // その他のエラー（マッピングエラーなど）
            String message = e.getMessage();
            if (message != null && message.contains("required")) {
                errors.add(relativePath + ": " + message);
            }
        }

        return errors;
    }

    /** SmallRye の検証例外からエラーメッセージを抽出。 */
    private List<String> extractValidationProblems(
            io.smallrye.config.ConfigValidationException exception) {
        List<String> problems = new ArrayList<>();
        String message = exception.getMessage();

        if (message != null) {
            // "property name is required" 形式のメッセージを抽出
            if (message.contains("is required")) {
                problems.add("Missing required property - " + message);
            } else {
                problems.add(message);
            }
        }

        return problems;
    }

    /** タスクファイルから TaskInfo を抽出。 */
    private @Nullable TaskInfo extractTaskInfo(Path taskFile) {
        try {
            YamlConfigSource source = new YamlConfigSource(taskFile.toUri().toURL());
            Map<String, String> props = source.getProperties();

            String target = props.getOrDefault("target", "");
            List<String> dependencies = new ArrayList<>();

            // dependencies を抽出（リスト形式）
            for (Map.Entry<String, String> entry : props.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("dependencies[") && key.endsWith("]")) {
                    dependencies.add(entry.getValue());
                }
            }

            return new TaskInfo(target, dependencies);
        } catch (Exception e) {
            return null;
        }
    }

    /** 循環依存を検出する（DFS）。 */
    private List<String> detectCycles(Map<NodeId, TaskInfo> tasks) {
        List<String> errors = new ArrayList<>();
        Set<NodeId> visited = new HashSet<>();
        Set<NodeId> recursionStack = new HashSet<>();
        List<NodeId> path = new ArrayList<>();

        for (NodeId nodeId : tasks.keySet()) {
            if (!visited.contains(nodeId)) {
                String cycleError = detectCyclesDFS(nodeId, tasks, visited, recursionStack, path);
                if (cycleError != null) {
                    errors.add(cycleError);
                    break; // 最初のサイクルのみ報告
                }
            }
        }

        return errors;
    }

    /** DFS でサイクルを検出。 */
    private @Nullable String detectCyclesDFS(
            NodeId nodeId,
            Map<NodeId, TaskInfo> tasks,
            Set<NodeId> visited,
            Set<NodeId> recursionStack,
            List<NodeId> path) {

        visited.add(nodeId);
        recursionStack.add(nodeId);
        path.add(nodeId);

        TaskInfo taskInfo = tasks.get(nodeId);
        if (taskInfo != null) {
            for (String depIdStr : taskInfo.dependencies()) {
                NodeId depId = NodeId.of(depIdStr);

                if (!tasks.containsKey(depId)) {
                    // 存在しない依存先（別のエラーで報告済み）
                    continue;
                }

                if (recursionStack.contains(depId)) {
                    // サイクル検出
                    int cycleStart = path.indexOf(depId);
                    List<NodeId> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                    cycle.add(depId);
                    return "Circular dependency detected: "
                            + cycle.stream()
                                    .map(NodeId::value)
                                    .reduce((a, b) -> a + " -> " + b)
                                    .orElse("");
                }

                if (!visited.contains(depId)) {
                    String cycleError =
                            detectCyclesDFS(depId, tasks, visited, recursionStack, path);
                    if (cycleError != null) {
                        return cycleError;
                    }
                }
            }
        }

        recursionStack.remove(nodeId);
        path.remove(path.size() - 1);
        return null;
    }
}
