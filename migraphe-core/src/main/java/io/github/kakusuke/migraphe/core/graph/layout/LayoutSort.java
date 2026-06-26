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

/**
 * Topological sort tuned for layout, implemented with Kahn's algorithm.
 *
 * <p>This is the first stage of the ASCII layout pipeline ({@code MigrationGraph -> LayoutSort ->
 * LayoutTree -> GridCanvas -> ExecutionGraphView}). It orders the nodes so that dependencies always
 * precede their dependents and produces a stable, deterministic ordering by breaking ties on degree
 * and then node id. The resulting {@link LayoutOrder} feeds {@link LayoutTree#build(MigrationGraph,
 * LayoutOrder)}.
 *
 * <p>This is a static utility class and cannot be instantiated.
 */
public final class LayoutSort {

    private LayoutSort() {}

    /**
     * The result of a layout sort: the nodes in topological order plus their assigned ranks.
     *
     * @param nodes the nodes in topological (layout) order; copied defensively to an immutable list
     * @param rankMap a map from each node id to its zero-based position in {@link #nodes}; copied
     *     defensively to an immutable map
     */
    public record LayoutOrder(List<MigrationNode> nodes, Map<NodeId, Integer> rankMap) {

        /** Canonical constructor that defensively copies the node list and rank map. */
        public LayoutOrder {
            nodes = List.copyOf(nodes);
            rankMap = Map.copyOf(rankMap);
        }

        /**
         * Returns the rank (sort position) assigned to the given node.
         *
         * @param nodeId the node to look up
         * @return the zero-based rank, or {@code 0} if the node is not present in this order
         */
        public int rank(NodeId nodeId) {
            return rankMap.getOrDefault(nodeId, 0);
        }
    }

    /**
     * Sorts the nodes of the graph into a deterministic layout order.
     *
     * <p>Roots (in-degree 0) are processed first; among ready nodes the priority order is {@code
     * (-inDegree, -outDegree, id ascending)}, so nodes with more dependencies and more dependents
     * come earlier and ties break on the node id value.
     *
     * @param graph the migration graph to order
     * @return the topological layout order, including the per-node rank map
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
