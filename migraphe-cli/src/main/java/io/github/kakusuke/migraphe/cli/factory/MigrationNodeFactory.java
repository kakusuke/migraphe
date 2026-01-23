package io.github.kakusuke.migraphe.cli.factory;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.core.config.ConfigurationException;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import io.smallrye.config.SmallRyeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TaskDefinition から MigrationNode を生成する汎用ファクトリ。
 *
 * <p>プラグインを使用して MigrationNode を生成する。
 */
public class MigrationNodeFactory {

    private final PluginRegistry pluginRegistry;
    private final SmallRyeConfig config;

    public MigrationNodeFactory(PluginRegistry pluginRegistry, SmallRyeConfig config) {
        this.pluginRegistry = pluginRegistry;
        this.config = config;
    }

    /**
     * TaskDefinition から MigrationNode を生成する。
     *
     * @param taskDef タスク定義
     * @param nodeId ノードID
     * @param environment 実行環境
     * @return MigrationNode
     */
    public MigrationNode createNode(
            TaskDefinition<?> taskDef, NodeId nodeId, Environment environment) {

        // ターゲットの type を取得してプラグインを特定
        String targetId = taskDef.target();
        String type = config.getValue("target." + targetId + ".type", String.class);

        MigraphePlugin<?> plugin = pluginRegistry.getRequiredPlugin(type);

        @SuppressWarnings("unchecked")
        MigraphePlugin<Object> typedPlugin = (MigraphePlugin<Object>) plugin;

        // 依存関係を解決（フレームワークの責務）
        Set<NodeId> dependencies = resolveDependencies(taskDef);

        // プラグインの MigrationNodeProvider で MigrationNode を生成
        @SuppressWarnings("unchecked")
        TaskDefinition<Object> typedTaskDef = (TaskDefinition<Object>) taskDef;
        return typedPlugin
                .migrationNodeProvider()
                .createNode(nodeId, typedTaskDef, dependencies, environment);
    }

    /**
     * 複数の TaskDefinition から MigrationNode のリストを生成する。
     *
     * @param taskDefinitions NodeId → TaskDefinition のマップ
     * @param environments ターゲットID → Environment のマップ
     * @return MigrationNode のリスト
     * @throws ConfigurationException ターゲットに対応する Environment が見つからない場合
     */
    public List<MigrationNode> createNodes(
            Map<NodeId, TaskDefinition<?>> taskDefinitions, Map<String, Environment> environments) {

        List<MigrationNode> nodes = new ArrayList<>();

        for (Map.Entry<NodeId, TaskDefinition<?>> entry : taskDefinitions.entrySet()) {
            NodeId nodeId = entry.getKey();
            TaskDefinition<?> taskDef = entry.getValue();

            // ターゲットIDからEnvironmentを取得
            String targetId = taskDef.target();
            Environment environment = environments.get(targetId);

            if (environment == null) {
                throw new ConfigurationException("Environment not found for target: " + targetId);
            }

            MigrationNode node = createNode(taskDef, nodeId, environment);
            nodes.add(node);
        }

        return nodes;
    }

    /**
     * TaskDefinition から依存関係を解決する。
     *
     * @param taskDef タスク定義
     * @return 依存ノードID のセット
     */
    private Set<NodeId> resolveDependencies(TaskDefinition<?> taskDef) {
        return taskDef.dependencies()
                .map(deps -> deps.stream().map(NodeId::of).collect(Collectors.toSet()))
                .orElse(Set.of());
    }
}
