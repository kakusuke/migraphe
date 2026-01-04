package io.github.migraphe.cli.factory;

import io.github.migraphe.core.config.ConfigurationException;
import io.github.migraphe.core.config.TaskConfig;
import io.github.migraphe.core.graph.NodeId;
import io.github.migraphe.postgresql.PostgreSQLEnvironment;
import io.github.migraphe.postgresql.PostgreSQLMigrationNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** TaskConfig から PostgreSQLMigrationNode を生成するファクトリ。 */
public class MigrationNodeFactory {

    /**
     * TaskConfig から PostgreSQLMigrationNode を生成する。
     *
     * @param taskConfig タスク設定
     * @param nodeId ノードID
     * @param environment 実行環境
     * @return PostgreSQLMigrationNode
     */
    public PostgreSQLMigrationNode createNode(
            TaskConfig taskConfig, NodeId nodeId, PostgreSQLEnvironment environment) {

        // Builder pattern for PostgreSQLMigrationNode
        var builder =
                PostgreSQLMigrationNode.builder()
                        .id(nodeId.value())
                        .name(taskConfig.name())
                        .environment(environment)
                        .upSql(taskConfig.up().sql());

        // Dependencies (optional)
        taskConfig
                .dependencies()
                .ifPresent(
                        deps -> {
                            NodeId[] depIds = deps.stream().map(NodeId::of).toArray(NodeId[]::new);
                            builder.dependencies(depIds);
                        });

        // DOWN task (optional)
        taskConfig.down().ifPresent(down -> builder.downSql(down.sql()));

        return builder.build();
    }

    /**
     * 複数の TaskConfig から PostgreSQLMigrationNode のリストを生成する。
     *
     * @param taskConfigs NodeId → TaskConfig のマップ
     * @param environments ターゲットID → Environment のマップ
     * @return PostgreSQLMigrationNode のリスト
     * @throws ConfigurationException ターゲットに対応する Environment が見つからない場合
     */
    public List<PostgreSQLMigrationNode> createNodes(
            Map<NodeId, TaskConfig> taskConfigs, Map<String, PostgreSQLEnvironment> environments) {

        List<PostgreSQLMigrationNode> nodes = new ArrayList<>();

        for (Map.Entry<NodeId, TaskConfig> entry : taskConfigs.entrySet()) {
            NodeId nodeId = entry.getKey();
            TaskConfig taskConfig = entry.getValue();

            // ターゲットIDからEnvironmentを取得
            String targetId = taskConfig.target();
            PostgreSQLEnvironment environment = environments.get(targetId);

            if (environment == null) {
                throw new ConfigurationException("Environment not found for target: " + targetId);
            }

            PostgreSQLMigrationNode node = createNode(taskConfig, nodeId, environment);
            nodes.add(node);
        }

        return nodes;
    }
}
