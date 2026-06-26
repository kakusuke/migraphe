package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Contract for running a set of migration nodes over a {@link
 * io.github.kakusuke.migraphe.core.graph.MigrationGraph}.
 *
 * <p>The canonical implementation is {@link DagExecutor}, which unifies UP/DOWN and
 * sequential/parallel execution behind this interface. A typical caller first computes the target
 * node set with {@link #determineTargetNodes} (or, for rollbacks, {@link
 * DagExecutor#determineRollbackTargets}) and then passes it to {@link #execute}.
 */
public interface Executor {

    /**
     * Determines the set of nodes to execute.
     *
     * <p>Implementations typically expand {@code targetId} to include its transitive dependencies
     * and filter out nodes already in their target state.
     *
     * @param targetId a specific target node, or {@code null} to consider all nodes in the graph
     * @return the set of node IDs that still need to be executed
     */
    Set<NodeId> determineTargetNodes(@Nullable NodeId targetId);

    /**
     * Executes the given set of target nodes.
     *
     * @param targetNodes the node IDs to execute, in this executor's configured direction
     * @return the {@link ExecutionResult} summarizing the run
     */
    ExecutionResult execute(Set<NodeId> targetNodes);
}
