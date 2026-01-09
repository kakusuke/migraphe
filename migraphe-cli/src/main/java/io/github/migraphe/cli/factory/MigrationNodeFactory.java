package io.github.migraphe.cli.factory;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.graph.MigrationNode;
import io.github.migraphe.api.graph.NodeId;
import io.github.migraphe.api.spi.MigraphePlugin;
import io.github.migraphe.api.spi.SqlDefinition;
import io.github.migraphe.api.spi.TaskDefinition;
import io.github.migraphe.core.config.ConfigurationException;
import io.github.migraphe.core.config.TaskConfig;
import io.github.migraphe.core.plugin.PluginRegistry;
import io.smallrye.config.SmallRyeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** TaskConfig から MigrationNode を生成する汎用ファクトリ。プラグインを使用して MigrationNode を生成する。 */
public class MigrationNodeFactory {

    private final PluginRegistry pluginRegistry;
    private final SmallRyeConfig config;

    public MigrationNodeFactory(PluginRegistry pluginRegistry, SmallRyeConfig config) {
        this.pluginRegistry = pluginRegistry;
        this.config = config;
    }

    /**
     * TaskConfig から MigrationNode を生成する。
     *
     * @param taskConfig タスク設定
     * @param nodeId ノードID
     * @param environment 実行環境
     * @return MigrationNode
     */
    public MigrationNode createNode(TaskConfig taskConfig, NodeId nodeId, Environment environment) {

        // ターゲットの type を取得してプラグインを特定
        String targetId = taskConfig.target();
        String type = config.getValue("target." + targetId + ".type", String.class);

        MigraphePlugin plugin =
                pluginRegistry
                        .getPlugin(type)
                        .orElseThrow(
                                () ->
                                        new ConfigurationException(
                                                "No plugin found for type: "
                                                        + type
                                                        + ". Available types: "
                                                        + pluginRegistry.supportedTypes()));

        // TaskConfig を TaskDefinition に変換
        TaskDefinition taskDef = toTaskDefinition(taskConfig);

        // 依存関係を解決（フレームワークの責務）
        Set<NodeId> dependencies = resolveDependencies(taskConfig);

        // プラグインの MigrationNodeProvider で MigrationNode を生成
        return plugin.migrationNodeProvider()
                .createNode(nodeId, taskDef, dependencies, environment);
    }

    /**
     * 複数の TaskConfig から MigrationNode のリストを生成する。
     *
     * @param taskConfigs NodeId → TaskConfig のマップ
     * @param environments ターゲットID → Environment のマップ
     * @return MigrationNode のリスト
     * @throws ConfigurationException ターゲットに対応する Environment が見つからない場合
     */
    public List<MigrationNode> createNodes(
            Map<NodeId, TaskConfig> taskConfigs, Map<String, Environment> environments) {

        List<MigrationNode> nodes = new ArrayList<>();

        for (Map.Entry<NodeId, TaskConfig> entry : taskConfigs.entrySet()) {
            NodeId nodeId = entry.getKey();
            TaskConfig taskConfig = entry.getValue();

            // ターゲットIDからEnvironmentを取得
            String targetId = taskConfig.target();
            Environment environment = environments.get(targetId);

            if (environment == null) {
                throw new ConfigurationException("Environment not found for target: " + targetId);
            }

            MigrationNode node = createNode(taskConfig, nodeId, environment);
            nodes.add(node);
        }

        return nodes;
    }

    /**
     * TaskConfig を TaskDefinition に変換する。
     *
     * @param taskConfig タスク設定
     * @return TaskDefinition
     */
    private TaskDefinition toTaskDefinition(TaskConfig taskConfig) {
        // UP SQL
        SqlDefinition upSql = SqlDefinition.ofSql(taskConfig.up().sql());

        // DOWN SQL (optional)
        SqlDefinition downSql = null;
        if (taskConfig.down().isPresent()) {
            downSql = SqlDefinition.ofSql(taskConfig.down().get().sql());
        }

        return TaskDefinition.of(
                taskConfig.name(), taskConfig.description().orElse(null), upSql, downSql);
    }

    /**
     * TaskConfig から依存関係を解決する。
     *
     * @param taskConfig タスク設定
     * @return 依存ノードID のセット
     */
    private Set<NodeId> resolveDependencies(TaskConfig taskConfig) {
        return taskConfig
                .dependencies()
                .map(deps -> deps.stream().map(NodeId::of).collect(Collectors.toSet()))
                .orElse(Set.of());
    }
}
