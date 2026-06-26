package io.github.kakusuke.migraphe.api.execution;

import io.github.kakusuke.migraphe.api.task.ExecutionDirection;

/**
 * An aggregate report of a completed migration run, delivered to an {@link ExecutionListener}.
 *
 * <p>The summary records the direction of the run together with counts of how many nodes were
 * considered, executed, skipped and failed, and an overall {@link #success() success} flag.
 *
 * @param direction the direction in which the run was performed
 * @param totalNodes the total number of nodes considered by the run
 * @param executedCount the number of nodes that executed successfully
 * @param skippedCount the number of nodes that were skipped
 * @param failedCount the number of nodes that failed
 * @param success {@code true} if the run completed without any failures
 * @see ExecutionListener
 */
public record ExecutionSummary(
        ExecutionDirection direction,
        int totalNodes,
        int executedCount,
        int skippedCount,
        int failedCount,
        boolean success) {

    /**
     * Creates a summary for a successful run (no failures).
     *
     * @param direction the direction in which the run was performed
     * @param total the total number of nodes considered
     * @param executed the number of nodes that executed successfully
     * @param skipped the number of nodes that were skipped
     * @return a summary with a failed count of zero and {@code success} set to {@code true}
     */
    public static ExecutionSummary success(
            ExecutionDirection direction, int total, int executed, int skipped) {
        return new ExecutionSummary(direction, total, executed, skipped, 0, true);
    }

    /**
     * Creates a summary for a run that had one or more failures.
     *
     * @param direction the direction in which the run was performed
     * @param total the total number of nodes considered
     * @param executed the number of nodes that executed successfully
     * @param skipped the number of nodes that were skipped
     * @param failed the number of nodes that failed
     * @return a summary with {@code success} set to {@code false}
     */
    public static ExecutionSummary failure(
            ExecutionDirection direction, int total, int executed, int skipped, int failed) {
        return new ExecutionSummary(direction, total, executed, skipped, failed, false);
    }
}
