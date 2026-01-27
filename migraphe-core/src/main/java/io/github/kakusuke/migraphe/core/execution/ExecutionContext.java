package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.core.config.ConfigLoader;
import io.github.kakusuke.migraphe.core.factory.EnvironmentFactory;
import io.github.kakusuke.migraphe.core.factory.MigrationNodeFactory;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import io.smallrye.config.SmallRyeConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 実行時のコンテキスト。
 *
 * <p>プロジェクトの設定、環境、マイグレーションノード、グラフ、プラグインレジストリを保持する。
 *
 * @param baseDir プロジェクトのベースディレクトリ
 * @param config MicroProfile Config
 * @param pluginRegistry プラグインレジストリ
 * @param environments ターゲットID → Environment のマップ
 * @param nodes マイグレーションノードのリスト
 * @param graph マイグレーショングラフ
 */
public record ExecutionContext(
        Path baseDir,
        SmallRyeConfig config,
        PluginRegistry pluginRegistry,
        Map<String, Environment> environments,
        List<MigrationNode> nodes,
        MigrationGraph graph) {

    /**
     * プロジェクトディレクトリから ExecutionContext をロードする。
     *
     * @param baseDir プロジェクトのルートディレクトリ
     * @param pluginRegistry プラグインレジストリ
     * @return ExecutionContext
     */
    public static ExecutionContext load(Path baseDir, PluginRegistry pluginRegistry) {
        // 1. ConfigLoader でYAML設定を読み込み
        ConfigLoader configLoader = new ConfigLoader();
        SmallRyeConfig config = configLoader.load(baseDir);

        // 2. EnvironmentDefinition を読み込み、EnvironmentFactory で全Environment生成
        Map<String, EnvironmentDefinition> environmentDefinitions =
                configLoader.loadEnvironmentDefinitions(config, pluginRegistry);
        EnvironmentFactory environmentFactory = new EnvironmentFactory(pluginRegistry);
        Map<String, Environment> environments =
                environmentFactory.createEnvironments(environmentDefinitions);

        // 3. ConfigLoader で TaskDefinition を読み込み（プラグイン固有の型でマッピング）
        Map<NodeId, TaskDefinition<?>> taskDefinitions =
                configLoader.loadTaskDefinitions(baseDir, config, pluginRegistry);

        // 4. MigrationNodeFactory でノードを生成
        MigrationNodeFactory nodeFactory = new MigrationNodeFactory(pluginRegistry, config);
        List<MigrationNode> nodes = nodeFactory.createNodes(taskDefinitions, environments);

        // 5. MigrationGraph を構築（依存関係順にノードを追加）
        MigrationGraph graph = MigrationGraph.create();
        List<MigrationNode> sortedNodes = sortNodesByDependencies(nodes);
        for (MigrationNode node : sortedNodes) {
            graph.addNode(node);
        }

        return new ExecutionContext(
                baseDir, config, pluginRegistry, environments, sortedNodes, graph);
    }

    /**
     * ノードを依存関係順にソートする（トポロジカルソート）。
     *
     * @param nodes ソート前のノードリスト
     * @return 依存関係順にソートされたノードリスト
     */
    private static List<MigrationNode> sortNodesByDependencies(List<MigrationNode> nodes) {

        // ノードIDでマップ化
        Map<NodeId, MigrationNode> nodeMap = new HashMap<>();
        for (MigrationNode node : nodes) {
            nodeMap.put(node.id(), node);
        }

        List<MigrationNode> sorted = new ArrayList<>();
        Set<NodeId> visited = new HashSet<>();
        Set<NodeId> visiting = new HashSet<>();

        // 各ノードを訪問してトポロジカルソート
        for (MigrationNode node : nodes) {
            if (!visited.contains(node.id())) {
                visitNode(node, nodeMap, visited, visiting, sorted);
            }
        }

        return sorted;
    }

    /**
     * DFSでノードを訪問する。
     *
     * @param node 現在のノード
     * @param nodeMap 全ノードのマップ
     * @param visited 訪問済みノード
     * @param visiting 訪問中ノード（循環検出用）
     * @param sorted ソート結果
     */
    private static void visitNode(
            MigrationNode node,
            Map<NodeId, MigrationNode> nodeMap,
            Set<NodeId> visited,
            Set<NodeId> visiting,
            List<MigrationNode> sorted) {

        if (visiting.contains(node.id())) {
            throw new IllegalArgumentException("Circular dependency detected: " + node.id());
        }

        visiting.add(node.id());

        // 依存ノードを先に訪問
        for (NodeId depId : node.dependencies()) {
            MigrationNode depNode = nodeMap.get(depId);
            if (depNode != null && !visited.contains(depId)) {
                visitNode(depNode, nodeMap, visited, visiting, sorted);
            }
        }

        visiting.remove(node.id());
        visited.add(node.id());
        sorted.add(node);
    }
}
