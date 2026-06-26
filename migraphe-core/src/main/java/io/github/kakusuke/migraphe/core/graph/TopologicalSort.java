package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.*;
import java.util.Comparator;

/**
 * Builds level-grouped {@link ExecutionPlan execution plans} from a {@link MigrationGraph} via
 * topological sorting.
 *
 * <p>The algorithm is a variant of Kahn's algorithm: it repeatedly collects all nodes whose
 * dependencies are already satisfied (in-degree zero) into one {@link ExecutionLevel}, then removes
 * them and decrements their dependents' in-degrees, advancing level by level until every node is
 * placed. Within each level, nodes are ordered deterministically — first by descending subtree
 * depth (longest remaining path to a leaf, so that critical-path work surfaces early), then by node
 * id for a stable tiebreak.
 *
 * <p>The class exposes three planning entry points: a full forward plan over the whole graph, a
 * forward plan restricted to a target node subset, and a reverse plan over a subset for rollback.
 * This is a stateless utility and cannot be instantiated meaningfully.
 */
public final class TopologicalSort {

    /** Creates a new {@code TopologicalSort}. */
    public TopologicalSort() {}

    /**
     * Builds a forward parallel execution plan covering every node in the graph.
     *
     * <p>Nodes are grouped into levels so that all of a node's dependencies appear in earlier
     * levels; roots (no dependencies) form the first level.
     *
     * @param graph the migration graph to plan
     * @return a level-grouped execution plan in forward (dependency-respecting) order
     * @throws IllegalStateException if the graph contains a cycle
     */
    public static ExecutionPlan createExecutionPlan(MigrationGraph graph) {
        if (graph.hasCycle()) {
            throw new IllegalStateException("Cannot create execution plan: graph contains a cycle");
        }

        List<ExecutionLevel> levels = new ArrayList<>();
        Map<NodeId, Integer> inDegree = calculateInDegree(graph);
        int currentLevel = 0;

        Map<NodeId, Integer> subtreeDepths = computeSubtreeDepths(graph);

        while (!inDegree.isEmpty()) {
            // Collect nodes with in-degree 0 (all dependencies already resolved).
            List<MigrationNode> nodesAtCurrentLevel = new ArrayList<>();

            for (var entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    graph.getNode(entry.getKey()).ifPresent(nodesAtCurrentLevel::add);
                }
            }

            if (nodesAtCurrentLevel.isEmpty()) {
                throw new IllegalStateException("Graph contains a cycle or invalid dependencies");
            }

            nodesAtCurrentLevel.sort(
                    Comparator.comparingInt(
                                    (MigrationNode n) -> subtreeDepths.getOrDefault(n.id(), 0))
                            .reversed()
                            .thenComparing(n -> n.id().value()));

            levels.add(new ExecutionLevel(currentLevel, nodesAtCurrentLevel));

            // Remove the processed nodes and decrement their dependents' in-degrees.
            for (MigrationNode node : nodesAtCurrentLevel) {
                inDegree.remove(node.id());

                // Decrement the in-degree of nodes that depended on this node.
                for (NodeId dependent : graph.getDependents(node.id())) {
                    inDegree.computeIfPresent(dependent, (k, v) -> v - 1);
                }
            }

            currentLevel++;
        }

        return new ExecutionPlan(levels);
    }

    /** Computes each node's subtree depth (longest path to a leaf). */
    private static Map<NodeId, Integer> computeSubtreeDepths(MigrationGraph graph) {
        Map<NodeId, Integer> depth = new HashMap<>();
        for (MigrationNode node : graph.allNodes()) {
            computeDepth(node.id(), graph, depth);
        }
        return depth;
    }

    private static int computeDepth(NodeId id, MigrationGraph graph, Map<NodeId, Integer> memo) {
        if (memo.containsKey(id)) return memo.get(id);
        int max = 0;
        for (NodeId dep : graph.getDependents(id)) {
            max = Math.max(max, 1 + computeDepth(dep, graph, memo));
        }
        memo.put(id, max);
        return max;
    }

    /** Computes each node's in-degree (the number of nodes it depends on). */
    private static Map<NodeId, Integer> calculateInDegree(MigrationGraph graph) {
        Map<NodeId, Integer> inDegree = new HashMap<>();

        for (MigrationNode node : graph.allNodes()) {
            inDegree.put(node.id(), node.dependencies().size());
        }

        return inDegree;
    }

    /**
     * Builds a forward execution plan restricted to a subset of nodes.
     *
     * <p>A sub-DAG is induced over {@code targetNodes}: in-degrees count only dependencies that are
     * themselves members of {@code targetNodes}, so dependencies outside the subset are ignored.
     * The resulting plan executes the target nodes in dependency-respecting order. Subtree-depth
     * ordering for the in-level tiebreak is still computed against the full graph.
     *
     * @param graph the migration graph that the target nodes belong to
     * @param targetNodes the identifiers of the nodes to include in the plan
     * @return a forward execution plan over the target subset, or an empty plan if {@code
     *     targetNodes} is empty
     * @throws IllegalStateException if the induced subgraph has no schedulable node (invalid or
     *     cyclic dependencies)
     */
    public static ExecutionPlan createExecutionPlanFor(
            MigrationGraph graph, Set<NodeId> targetNodes) {
        if (targetNodes.isEmpty()) {
            return new ExecutionPlan(List.of());
        }

        // Build the sub-DAG over the target nodes only and compute in-degrees within it.
        Map<NodeId, Integer> inDegree = new HashMap<>();
        for (NodeId nodeId : targetNodes) {
            // Number of this node's dependencies that are themselves target nodes.
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

        Map<NodeId, Integer> subtreeDepthsFor = computeSubtreeDepths(graph);

        while (!inDegree.isEmpty()) {
            // Nodes with in-degree 0 (all dependencies resolved = ready to run).
            List<MigrationNode> nodesAtCurrentLevel = new ArrayList<>();

            for (var entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    graph.getNode(entry.getKey()).ifPresent(nodesAtCurrentLevel::add);
                }
            }

            if (nodesAtCurrentLevel.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot create execution plan: invalid dependencies");
            }

            nodesAtCurrentLevel.sort(
                    Comparator.comparingInt(
                                    (MigrationNode n) -> subtreeDepthsFor.getOrDefault(n.id(), 0))
                            .reversed()
                            .thenComparing(n -> n.id().value()));

            levels.add(new ExecutionLevel(currentLevel, nodesAtCurrentLevel));

            // Remove the processed nodes and decrement their dependents' in-degrees.
            for (MigrationNode node : nodesAtCurrentLevel) {
                inDegree.remove(node.id());

                // Decrement the in-degree of nodes that depend on this node (its dependents).
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
     * Builds a reverse (rollback) execution plan restricted to a subset of nodes.
     *
     * <p>For rollback, dependency edges are followed in reverse: a node's effective in-degree is
     * the number of its dependents (out-degree) that are also in {@code targetNodes}. Thus nodes
     * that nothing in the subset depends on are scheduled first, guaranteeing that every dependent
     * is undone before the node it relied on. Subtree-depth ordering for the in-level tiebreak is
     * computed against the full graph.
     *
     * @param graph the migration graph that the target nodes belong to
     * @param targetNodes the identifiers of the nodes to roll back
     * @return a reverse execution plan over the target subset, or an empty plan if {@code
     *     targetNodes} is empty
     * @throws IllegalStateException if the induced subgraph has no schedulable node (invalid or
     *     cyclic dependencies)
     */
    public static ExecutionPlan createReverseExecutionPlanFor(
            MigrationGraph graph, Set<NodeId> targetNodes) {
        if (targetNodes.isEmpty()) {
            return new ExecutionPlan(List.of());
        }

        // Build the sub-DAG over the target nodes and compute reverse in-degrees:
        // for rollback the in-degree is the out-degree (number of dependents).
        Map<NodeId, Integer> outDegree = new HashMap<>();
        for (NodeId nodeId : targetNodes) {
            // Number of nodes within the target set that depend on this node.
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

        Map<NodeId, Integer> subtreeDepthsRev = computeSubtreeDepths(graph);

        while (!outDegree.isEmpty()) {
            // Nodes with out-degree 0 (nothing depends on them = safe to roll back first).
            List<MigrationNode> nodesAtCurrentLevel = new ArrayList<>();

            for (var entry : outDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    graph.getNode(entry.getKey()).ifPresent(nodesAtCurrentLevel::add);
                }
            }

            if (nodesAtCurrentLevel.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot create reverse execution plan: invalid dependencies");
            }

            nodesAtCurrentLevel.sort(
                    Comparator.comparingInt(
                                    (MigrationNode n) -> subtreeDepthsRev.getOrDefault(n.id(), 0))
                            .reversed()
                            .thenComparing(n -> n.id().value()));

            levels.add(new ExecutionLevel(currentLevel, nodesAtCurrentLevel));

            // Remove the processed nodes and decrement their dependencies' out-degrees.
            for (MigrationNode node : nodesAtCurrentLevel) {
                outDegree.remove(node.id());

                // Decrement the out-degree of the nodes this node depends on (its dependencies).
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
