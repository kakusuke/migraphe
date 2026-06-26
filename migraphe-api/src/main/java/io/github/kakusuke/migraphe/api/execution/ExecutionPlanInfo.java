package io.github.kakusuke.migraphe.api.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import java.util.Set;

/**
 * A read-only snapshot of a computed execution plan, delivered to an {@link ExecutionListener}.
 *
 * <p>The plan groups the nodes to execute into dependency levels: nodes within the same level have
 * no dependencies among themselves and may run concurrently, while levels run in order. It also
 * carries which nodes are already applied and whether the run is a dry run, so that listeners can
 * present an accurate preview before execution begins.
 *
 * @param levels the nodes to execute, grouped into dependency levels that run in order; each inner
 *     list is a set of nodes that may run concurrently
 * @param executedNodes the identifiers of nodes already applied (and therefore skipped)
 * @param totalNodes the total number of nodes considered by the plan
 * @param isDryRun {@code true} if the plan describes a dry run in which nothing is actually
 *     executed
 * @see ExecutionListener
 * @see MigrationNode
 */
public record ExecutionPlanInfo(
        List<List<MigrationNode>> levels,
        Set<NodeId> executedNodes,
        int totalNodes,
        boolean isDryRun) {

    /**
     * Returns the number of nodes that still need to run.
     *
     * @return {@code totalNodes} minus the number of already-executed nodes
     */
    public int pendingNodes() {
        return totalNodes - executedNodes.size();
    }
}
