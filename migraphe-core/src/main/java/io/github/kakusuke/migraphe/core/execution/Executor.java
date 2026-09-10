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
 * node set with {@link #determineSelectedNodes} and then passes it to {@link #execute}.
 *
 * <p>A rollback has no counterpart here on purpose. What a {@code down} run removes is decided by
 * {@link DownService#plan}, whole: which nodes, what refuses the run, and the graph to execute
 * against all come back together, and a {@code --all} run that cannot take out everything refuses
 * rather than taking out part. A second entry point that answered only "which nodes" could not
 * carry that refusal, and once carried the two answers apart.
 */
public interface Executor {

    /**
     * Determines the set of nodes to execute.
     *
     * <p>Implementations typically expand {@code requestedNode} to include its transitive
     * dependencies and filter out nodes already in their target state.
     *
     * @param requestedNode a specific target node, or {@code null} to consider all nodes in the
     *     graph
     * @return the set of node IDs that still need to be executed
     */
    Set<NodeId> determineSelectedNodes(@Nullable NodeId requestedNode);

    /**
     * Executes the given set of target nodes.
     *
     * @param selectedNodes the node IDs to execute, in this executor's configured direction
     * @return the {@link ExecutionResult} summarizing the run
     */
    ExecutionResult execute(Set<NodeId> selectedNodes);
}
