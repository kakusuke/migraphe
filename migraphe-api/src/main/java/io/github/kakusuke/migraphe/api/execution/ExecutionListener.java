package io.github.kakusuke.migraphe.api.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import org.jspecify.annotations.Nullable;

/**
 * A callback interface that receives progress notifications during a migration run.
 *
 * <p>Migraphe's core orchestration emits lifecycle events through this listener so that
 * presentation layers can render progress without the core depending on any particular output
 * mechanism. The CLI and the Gradle plugin each implement this interface to format console or
 * build-log output.
 *
 * <p>Implementations should be lightweight and must not throw; an exception from a callback may
 * disrupt the run. When migrations execute in parallel, callbacks may be invoked from multiple
 * threads, so Migraphe wraps a listener in a synchronized decorator to serialize the notifications.
 *
 * @see ExecutionPlanInfo
 * @see ExecutionSummary
 * @see MigrationNode
 */
public interface ExecutionListener {

    /**
     * Invoked once the execution plan has been computed, before any node runs.
     *
     * @param plan the computed execution plan, including the nodes to run and dry-run flag
     */
    void onPlanCreated(ExecutionPlanInfo plan);

    /**
     * Invoked when a node begins executing.
     *
     * @param node the node that is starting
     * @param direction the direction in which the node is executing
     */
    void onNodeStarted(MigrationNode node, ExecutionDirection direction);

    /**
     * Invoked when a node completes successfully.
     *
     * @param node the node that succeeded
     * @param direction the direction in which the node executed
     * @param durationMs the execution duration in milliseconds
     */
    void onNodeSucceeded(MigrationNode node, ExecutionDirection direction, long durationMs);

    /**
     * Invoked when a node is skipped.
     *
     * @param node the node that was skipped
     * @param direction the direction in which the node would have executed
     * @param reason a human-readable explanation of why the node was skipped
     */
    void onNodeSkipped(MigrationNode node, ExecutionDirection direction, String reason);

    /**
     * Invoked when a node fails.
     *
     * @param node the node that failed
     * @param direction the direction in which the node was executing
     * @param sqlContent the SQL associated with the failure when available, or {@code null} if the
     *     task does not expose SQL content
     * @param errorMessage a human-readable description of the failure
     */
    void onNodeFailed(
            MigrationNode node,
            ExecutionDirection direction,
            @Nullable String sqlContent,
            String errorMessage);

    /**
     * Invoked once after the entire run finishes.
     *
     * @param summary the aggregate outcome of the run
     */
    void onCompleted(ExecutionSummary summary);
}
