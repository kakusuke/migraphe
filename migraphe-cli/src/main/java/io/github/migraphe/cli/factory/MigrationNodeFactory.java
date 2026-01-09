package io.github.migraphe.cli.factory;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.graph.MigrationNode;
import io.github.migraphe.api.graph.NodeId;
import io.github.migraphe.api.spi.MigraphePlugin;
import io.github.migraphe.core.config.ConfigurationException;
import io.github.migraphe.core.config.TaskConfig;
import io.github.migraphe.core.plugin.PluginRegistry;
import io.smallrye.config.SmallRyeConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        // TaskConfig を Map<String, Object> に変換
        Map<String, Object> taskConfigMap = toMap(taskConfig);

        // プラグインの MigrationNodeProvider で MigrationNode を生成
        return plugin.migrationNodeProvider().createNode(nodeId, taskConfigMap, environment);
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
     * TaskConfig を Map<String, Object> に変換する。
     *
     * @param taskConfig タスク設定
     * @return 設定のマップ表現
     */
    private Map<String, Object> toMap(TaskConfig taskConfig) {
        Map<String, Object> map = new HashMap<>();

        map.put("name", taskConfig.name());
        map.put("target", taskConfig.target());

        taskConfig.description().ifPresent(desc -> map.put("description", desc));
        taskConfig.dependencies().ifPresent(deps -> map.put("dependencies", deps));

        // up
        Map<String, Object> upMap = new HashMap<>();
        upMap.put("sql", taskConfig.up().sql());
        map.put("up", upMap);

        // down (optional)
        taskConfig
                .down()
                .ifPresent(
                        down -> {
                            Map<String, Object> downMap = new HashMap<>();
                            downMap.put("sql", down.sql());
                            map.put("down", downMap);
                        });

        return map;
    }
}
