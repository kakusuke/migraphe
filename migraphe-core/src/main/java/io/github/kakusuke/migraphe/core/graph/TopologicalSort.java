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
}
