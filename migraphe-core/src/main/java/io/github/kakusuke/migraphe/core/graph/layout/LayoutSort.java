package io.github.kakusuke.migraphe.core.graph.layout;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** レイアウト用トポロジカルソート。Kahn's アルゴリズムを使用。 */
public final class LayoutSort {

    private LayoutSort() {}

    /** ソート結果。ノードリストとランクマップを保持する。 */
    public record LayoutOrder(List<MigrationNode> nodes, Map<NodeId, Integer> rankMap) {

        public LayoutOrder {
            nodes = List.copyOf(nodes);
            rankMap = Map.copyOf(rankMap);
        }

        public int rank(NodeId nodeId) {
            return rankMap.getOrDefault(nodeId, 0);
        }
    }

    /**
     * グラフのノードをレイアウト用にソートする。
     *
     * <p>比較順: (-inDegree, -outDegree, id昇順)
     *
     * @param graph マイグレーショングラフ
     * @return ソート結果
     */
    public static LayoutOrder sort(MigrationGraph graph) {
        Map<NodeId, Integer> inDegree = new HashMap<>();
        for (MigrationNode node : graph.allNodes()) {
            inDegree.put(node.id(), graph.getDependencies(node.id()).size());
        }

        Comparator<NodeId> comparator =
                Comparator.comparingInt((NodeId id) -> -graph.getDependencies(id).size())
                        .thenComparingInt((NodeId id) -> -graph.getDependents(id).size())
                        .thenComparing(NodeId::value);

        PriorityQueue<NodeId> queue = new PriorityQueue<>(comparator);
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<MigrationNode> nodes = new ArrayList<>();
        Map<NodeId, Integer> rankMap = new HashMap<>();
        int rank = 0;

        while (!queue.isEmpty()) {
            NodeId current = queue.poll();
            graph.getNode(current).ifPresent(nodes::add);
            rankMap.put(current, rank++);

            for (NodeId dependent : graph.getDependents(current)) {
                inDegree.computeIfPresent(dependent, (k, v) -> v - 1);
                if (inDegree.getOrDefault(dependent, -1) == 0) {
                    queue.add(dependent);
                }
            }
        }

        return new LayoutOrder(nodes, rankMap);
    }
}
