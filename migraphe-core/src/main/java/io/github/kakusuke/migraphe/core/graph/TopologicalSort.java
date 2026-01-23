package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.*;

/** トポロジカルソートによる実行プラン生成。 Kahn's アルゴリズムを使用。 */
public final class TopologicalSort {

    /**
     * グラフから並列実行プランを生成する。
     *
     * @param graph マイグレーショングラフ
     * @return 並列実行プラン
     * @throws IllegalStateException グラフに循環がある場合
     */
    public static ExecutionPlan createExecutionPlan(MigrationGraph graph) {
        if (graph.hasCycle()) {
            throw new IllegalStateException("Cannot create execution plan: graph contains a cycle");
        }

        List<ExecutionLevel> levels = new ArrayList<>();
        Map<NodeId, Integer> inDegree = calculateInDegree(graph);
        int currentLevel = 0;

        while (!inDegree.isEmpty()) {
            // 入次数が0のノード（依存関係が全て解決済み）を取得
            Set<MigrationNode> nodesAtCurrentLevel = new HashSet<>();

            for (var entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    graph.getNode(entry.getKey()).ifPresent(nodesAtCurrentLevel::add);
                }
            }

            if (nodesAtCurrentLevel.isEmpty()) {
                throw new IllegalStateException("Graph contains a cycle or invalid dependencies");
            }

            levels.add(new ExecutionLevel(currentLevel, nodesAtCurrentLevel));

            // 処理したノードを削除し、依存元の入次数を減らす
            for (MigrationNode node : nodesAtCurrentLevel) {
                inDegree.remove(node.id());

                // このノードに依存していたノードの入次数を減らす
                for (NodeId dependent : graph.getDependents(node.id())) {
                    inDegree.computeIfPresent(dependent, (k, v) -> v - 1);
                }
            }

            currentLevel++;
        }

        return new ExecutionPlan(levels);
    }

    /** 各ノードの入次数（依存している数）を計算 */
    private static Map<NodeId, Integer> calculateInDegree(MigrationGraph graph) {
        Map<NodeId, Integer> inDegree = new HashMap<>();

        for (MigrationNode node : graph.allNodes()) {
            inDegree.put(node.id(), node.dependencies().size());
        }

        return inDegree;
    }

    /**
     * 指定されたノードセットのみを含む正順実行プランを生成する。 対象ノードを依存関係順に実行する順序を返す。
     *
     * @param graph マイグレーショングラフ
     * @param targetNodes 対象ノードIDのセット
     * @return 正順実行プラン
     */
    public static ExecutionPlan createExecutionPlanFor(
            MigrationGraph graph, Set<NodeId> targetNodes) {
        if (targetNodes.isEmpty()) {
            return new ExecutionPlan(List.of());
        }

        // 対象ノードのみでサブグラフを構築し、入次数を計算
        Map<NodeId, Integer> inDegree = new HashMap<>();
        for (NodeId nodeId : targetNodes) {
            // 対象ノード内で、このノードが依存するノードの数
            int count = 0;
            for (NodeId dependency : graph.getDependencies(nodeId)) {
                if (targetNodes.contains(dependency)) {
                    count++;
                }
            }
            inDegree.put(nodeId, count);
        }

        List<ExecutionLevel> levels = new ArrayList<>();
        int currentLevel = 0;

        while (!inDegree.isEmpty()) {
            // 入次数が0のノード（依存関係が全て解決済み = 実行可能）
            Set<MigrationNode> nodesAtCurrentLevel = new HashSet<>();

            for (var entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    graph.getNode(entry.getKey()).ifPresent(nodesAtCurrentLevel::add);
                }
            }

            if (nodesAtCurrentLevel.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot create execution plan: invalid dependencies");
            }

            levels.add(new ExecutionLevel(currentLevel, nodesAtCurrentLevel));

            // 処理したノードを削除し、依存元の入次数を減らす
            for (MigrationNode node : nodesAtCurrentLevel) {
                inDegree.remove(node.id());

                // このノードに依存しているノード（= 依存元）の入次数を減らす
                for (NodeId dependent : graph.getDependents(node.id())) {
                    if (inDegree.containsKey(dependent)) {
                        inDegree.computeIfPresent(dependent, (k, v) -> v - 1);
                    }
                }
            }

            currentLevel++;
        }

        return new ExecutionPlan(levels);
    }

    /**
     * 指定されたノードセットのみを含む逆順実行プランを生成する。 ロールバック用に、依存されている側から先に実行される順序を返す。
     *
     * @param graph マイグレーショングラフ
     * @param targetNodes 対象ノードIDのセット
     * @return 逆順実行プラン
     */
    public static ExecutionPlan createReverseExecutionPlanFor(
            MigrationGraph graph, Set<NodeId> targetNodes) {
        if (targetNodes.isEmpty()) {
            return new ExecutionPlan(List.of());
        }

        // 対象ノードのみでサブグラフを構築し、逆方向の入次数を計算
        // 逆方向 = 依存されている数（出次数）を入次数として扱う
        Map<NodeId, Integer> outDegree = new HashMap<>();
        for (NodeId nodeId : targetNodes) {
            // 対象ノード内で、このノードに依存しているノードの数
            int count = 0;
            for (NodeId dependent : graph.getDependents(nodeId)) {
                if (targetNodes.contains(dependent)) {
                    count++;
                }
            }
            outDegree.put(nodeId, count);
        }

        List<ExecutionLevel> levels = new ArrayList<>();
        int currentLevel = 0;

        while (!outDegree.isEmpty()) {
            // 出次数が0のノード（誰からも依存されていない = 最初にロールバック可能）
            Set<MigrationNode> nodesAtCurrentLevel = new HashSet<>();

            for (var entry : outDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    graph.getNode(entry.getKey()).ifPresent(nodesAtCurrentLevel::add);
                }
            }

            if (nodesAtCurrentLevel.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot create reverse execution plan: invalid dependencies");
            }

            levels.add(new ExecutionLevel(currentLevel, nodesAtCurrentLevel));

            // 処理したノードを削除し、依存先の出次数を減らす
            for (MigrationNode node : nodesAtCurrentLevel) {
                outDegree.remove(node.id());

                // このノードが依存しているノード（= 依存先）の出次数を減らす
                for (NodeId dependency : node.dependencies()) {
                    if (outDegree.containsKey(dependency)) {
                        outDegree.computeIfPresent(dependency, (k, v) -> v - 1);
                    }
                }
            }

            currentLevel++;
        }

        return new ExecutionPlan(levels);
    }
}
