package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An ordered, level-grouped plan describing how migration nodes are executed.
 *
 * <p>An execution plan is the output of {@link TopologicalSort}. It partitions the nodes into a
 * sequence of {@link ExecutionLevel levels} such that all dependencies of a node appear in an
 * earlier level; consequently the nodes within a single level are independent of one another and
 * may be executed in parallel, while the levels themselves must be processed in order.
 *
 * @param levels the execution levels in the order they must be run; defensively copied to an
 *     immutable list
 */
public record ExecutionPlan(List<ExecutionLevel> levels) {

    /**
     * Canonical constructor that defensively copies {@code levels} into an immutable list.
     *
     * @param levels the execution levels in execution order
     */
    public ExecutionPlan {
        levels = List.copyOf(levels);
    }

    /**
     * Returns the total number of nodes across all levels of the plan.
     *
     * @return the sum of the sizes of every {@link ExecutionLevel}
     */
    public int totalNodes() {
        return levels.stream().mapToInt(ExecutionLevel::size).sum();
    }

    /**
     * Returns the maximum number of nodes that can run in parallel.
     *
     * <p>This is the size of the largest level, i.e. the widest point of the plan.
     *
     * @return the size of the largest level, or {@code 0} if the plan is empty
     */
    public int maxParallelism() {
        return levels.stream().mapToInt(ExecutionLevel::size).max().orElse(0);
    }

    /**
     * Returns the number of execution levels in the plan.
     *
     * @return the level count, equivalently the length of the critical (dependency) path
     */
    public int levelCount() {
        return levels.size();
    }

    /**
     * Filters the plan's nodes out of the supplied list, preserving that list's order.
     *
     * <p>Returns only those nodes from {@code allNodes} that are part of this plan, keeping them in
     * the order in which they appear in {@code allNodes} (for example a depth-first ordering)
     * rather than the plan's level ordering. Useful for presenting plan membership in a
     * caller-defined sequence.
     *
     * @param allNodes the source list of nodes to filter (for example in DFS order)
     * @return the subset of {@code allNodes} contained in this plan, in {@code allNodes} order
     */
    public List<MigrationNode> filterNodesInOrder(List<MigrationNode> allNodes) {
        Set<NodeId> planNodeIds = new HashSet<>();
        for (ExecutionLevel level : levels) {
            for (MigrationNode n : level.nodes()) {
                planNodeIds.add(n.id());
            }
        }
        List<MigrationNode> filtered = new ArrayList<>();
        for (MigrationNode node : allNodes) {
            if (planNodeIds.contains(node.id())) {
                filtered.add(node);
            }
        }
        return filtered;
    }
}
