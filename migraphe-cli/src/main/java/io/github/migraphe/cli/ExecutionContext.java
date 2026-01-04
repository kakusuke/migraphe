package io.github.migraphe.cli;

import io.github.migraphe.api.graph.NodeId;
import io.github.migraphe.cli.config.ConfigLoader;
import io.github.migraphe.cli.config.TaskIdGenerator;
import io.github.migraphe.cli.factory.EnvironmentFactory;
import io.github.migraphe.cli.factory.MigrationNodeFactory;
import io.github.migraphe.core.config.TaskConfig;
import io.github.migraphe.core.graph.MigrationGraph;
import io.github.migraphe.postgresql.PostgreSQLEnvironment;
import io.github.migraphe.postgresql.PostgreSQLMigrationNode;
import io.smallrye.config.SmallRyeConfig;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CLI実行時のコンテキスト。
 *
 * <p>プロジェクトの設定、環境、マイグレーションノード、グラフを保持する。
 *
 * @param baseDir プロジェクトのベースディレクトリ
 * @param config MicroProfile Config
 * @param environments ターゲットID → PostgreSQLEnvironment のマップ
 * @param nodes マイグレーションノードのリスト
 * @param graph マイグレーショングラフ
 */
public record ExecutionContext(
        Path baseDir,
        SmallRyeConfig config,
        Map<String, PostgreSQLEnvironment> environments,
        List<PostgreSQLMigrationNode> nodes,
        MigrationGraph graph) {

    /**
     * プロジェクトディレクトリから ExecutionContext をロードする。
     *
     * @param baseDir プロジェクトのルートディレクトリ
     * @return ExecutionContext
     */
    public static ExecutionContext load(Path baseDir) {
        // 1. ConfigLoader でYAML設定を読み込み
        ConfigLoader configLoader = new ConfigLoader();
        SmallRyeConfig config = configLoader.load(baseDir);

        // 2. EnvironmentFactory で全Environment生成
        EnvironmentFactory environmentFactory = new EnvironmentFactory();
        Map<String, PostgreSQLEnvironment> environments =
                environmentFactory.createEnvironments(config);

        // 3. TaskIdGenerator でタスクIDを生成し、TaskConfigを取得
        TaskIdGenerator taskIdGenerator = new TaskIdGenerator();
        Map<NodeId, TaskConfig> taskConfigs = loadTaskConfigs(baseDir, config, taskIdGenerator);

        // 4. MigrationNodeFactory でノードを生成
        MigrationNodeFactory nodeFactory = new MigrationNodeFactory();
        List<PostgreSQLMigrationNode> nodes = nodeFactory.createNodes(taskConfigs, environments);

        // 5. MigrationGraph を構築（依存関係順にノードを追加）
        MigrationGraph graph = MigrationGraph.create();
        List<PostgreSQLMigrationNode> sortedNodes = sortNodesByDependencies(nodes);
        for (PostgreSQLMigrationNode node : sortedNodes) {
            graph.addNode(node);
        }

        return new ExecutionContext(baseDir, config, environments, sortedNodes, graph);
    }

    /**
     * tasks/ ディレクトリから TaskConfig を読み込む。
     *
     * @param baseDir プロジェクトのルートディレクトリ
     * @param config SmallRyeConfig
     * @param taskIdGenerator TaskIdGenerator
     * @return NodeId → TaskConfig のマップ
     */
    private static Map<NodeId, TaskConfig> loadTaskConfigs(
            Path baseDir, SmallRyeConfig config, TaskIdGenerator taskIdGenerator) {

        Map<NodeId, TaskConfig> taskConfigs = new LinkedHashMap<>();
        Path tasksDir = baseDir.resolve("tasks");

        // tasks/ ディレクトリ内の全YAMLファイルを探索
        ConfigLoader configLoader = new ConfigLoader();
        Map<Path, SmallRyeConfig> taskFileConfigs = configLoader.loadTaskConfigs(tasksDir);

        for (Map.Entry<Path, SmallRyeConfig> entry : taskFileConfigs.entrySet()) {
            Path taskFile = entry.getKey();
            SmallRyeConfig taskConfig = entry.getValue();

            // ファイルパスからNodeIdを生成
            NodeId nodeId = taskIdGenerator.generateTaskId(baseDir, taskFile);

            // TaskConfig として取得
            TaskConfig task = taskConfig.getConfigMapping(TaskConfig.class);
            taskConfigs.put(nodeId, task);
        }

        return taskConfigs;
    }

    /**
     * ノードを依存関係順にソートする（トポロジカルソート）。
     *
     * @param nodes ソート前のノードリスト
     * @return 依存関係順にソートされたノードリスト
     */
    private static List<PostgreSQLMigrationNode> sortNodesByDependencies(
            List<PostgreSQLMigrationNode> nodes) {

        // ノードIDでマップ化
        Map<NodeId, PostgreSQLMigrationNode> nodeMap = new java.util.HashMap<>();
        for (PostgreSQLMigrationNode node : nodes) {
            nodeMap.put(node.id(), node);
        }

        List<PostgreSQLMigrationNode> sorted = new java.util.ArrayList<>();
        java.util.Set<NodeId> visited = new java.util.HashSet<>();
        java.util.Set<NodeId> visiting = new java.util.HashSet<>();

        // 各ノードを訪問してトポロジカルソート
        for (PostgreSQLMigrationNode node : nodes) {
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
            PostgreSQLMigrationNode node,
            Map<NodeId, PostgreSQLMigrationNode> nodeMap,
            java.util.Set<NodeId> visited,
            java.util.Set<NodeId> visiting,
            List<PostgreSQLMigrationNode> sorted) {

        if (visiting.contains(node.id())) {
            throw new IllegalArgumentException("Circular dependency detected: " + node.id());
        }

        visiting.add(node.id());

        // 依存ノードを先に訪問
        for (NodeId depId : node.dependencies()) {
            PostgreSQLMigrationNode depNode = nodeMap.get(depId);
            if (depNode != null && !visited.contains(depId)) {
                visitNode(depNode, nodeMap, visited, visiting, sorted);
            }
        }

        visiting.remove(node.id());
        visited.add(node.id());
        sorted.add(node);
    }
}
