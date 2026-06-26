package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.execution.ExecutionSummary;

/**
 * Outcome of a migration run produced by an {@link Executor}.
 *
 * <p>Pairs the detailed {@link ExecutionSummary} (executed/skipped/failed counts) with a single
 * boolean indicating overall success. Instances are created via the {@link #success} and {@link
 * #failure} factory methods.
 *
 * @param summary the detailed summary of the run
 * @param success {@code true} if the run completed without any node failure, {@code false}
 *     otherwise
 */
public record ExecutionResult(ExecutionSummary summary, boolean success) {

    /**
     * Creates a successful result.
     *
     * @param summary the summary describing the successful run
     * @return an {@code ExecutionResult} with {@code success == true}
     */
    public static ExecutionResult success(ExecutionSummary summary) {
        return new ExecutionResult(summary, true);
    }

    /**
     * Creates a failure result.
     *
     * @param summary the summary describing the run, including failure counts
     * @return an {@code ExecutionResult} with {@code success == false}
     */
    public static ExecutionResult failure(ExecutionSummary summary) {
        return new ExecutionResult(summary, false);
    }
}
