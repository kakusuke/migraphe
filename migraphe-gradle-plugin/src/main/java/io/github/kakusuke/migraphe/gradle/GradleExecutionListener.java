package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.execution.ExecutionListener;
import io.github.kakusuke.migraphe.api.execution.ExecutionPlanInfo;
import io.github.kakusuke.migraphe.api.execution.ExecutionSummary;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * {@link ExecutionListener} implementation that reports migration progress through Gradle's {@link
 * Logger} API.
 *
 * <p>Used by {@link MigrapheUpTask} and {@link MigrapheDownTask}, this listener logs per-node
 * success, skip and failure events at the appropriate Gradle log levels, and accumulates failure
 * and dependency-skip records so that a consolidated summary can be printed when the run completes.
 * The internal record collections are guarded by synchronization because callbacks may be invoked
 * from the executor's worker (virtual) threads during parallel execution.
 */
public final class GradleExecutionListener implements ExecutionListener {

    private final Logger logger;
    private final List<FailureRecord> failures = new ArrayList<>();
    private final List<SkippedRecord> dependencySkips = new ArrayList<>();

    /**
     * Creates a listener that writes to the given Gradle logger.
     *
     * @param logger the Gradle logger to write progress and summary messages to
     */
    public GradleExecutionListener(Logger logger) {
        this.logger = logger;
    }

    /**
     * Called when the execution plan has been created. This implementation does nothing.
     *
     * @param plan information about the created execution plan
     */
    @Override
    public void onPlanCreated(ExecutionPlanInfo plan) {
        // No output on plan creation.
    }

    /**
     * Called when a node starts executing. This implementation does nothing.
     *
     * @param node the node that started
     * @param direction the direction of execution ({@link ExecutionDirection#UP} or {@link
     *     ExecutionDirection#DOWN})
     */
    @Override
    public void onNodeStarted(MigrationNode node, ExecutionDirection direction) {
        // No output on node start.
    }

    /**
     * Called when a node finishes successfully; logs an {@code [OK]} line with its duration.
     *
     * @param node the node that succeeded
     * @param direction the direction of execution
     * @param durationMs the execution duration in milliseconds
     */
    @Override
    public void onNodeSucceeded(MigrationNode node, ExecutionDirection direction, long durationMs) {
        logger.lifecycle("[OK]   {} - {} ({}ms)", node.id().value(), node.name(), durationMs);
    }

    /**
     * Called when a node is skipped; logs a {@code [SKIP]} line and, when the reason indicates a
     * failed dependency, records it for the failure summary.
     *
     * @param node the node that was skipped
     * @param direction the direction of execution
     * @param reason the human-readable reason the node was skipped
     */
    @Override
    public void onNodeSkipped(MigrationNode node, ExecutionDirection direction, String reason) {
        logger.lifecycle("[SKIP] {} - {} ({})", node.id().value(), node.name(), reason);
        if (reason.startsWith("dependency failed:")) {
            synchronized (dependencySkips) {
                dependencySkips.add(new SkippedRecord(node.id().value(), node.name(), reason));
            }
        }
    }

    /**
     * Called when a node fails; logs a {@code [FAIL]} line, a detailed failure block (environment,
     * the offending SQL with line numbers when available, and the error message), and records the
     * failure for the final summary.
     *
     * @param node the node that failed
     * @param direction the direction of execution
     * @param sqlContent the SQL content that was being executed, or {@code null} if not applicable
     * @param errorMessage the error message describing the failure
     */
    @Override
    public void onNodeFailed(
            MigrationNode node,
            ExecutionDirection direction,
            @Nullable String sqlContent,
            String errorMessage) {
        logger.error("[FAIL] {} - {}", node.id().value(), node.name());
        logger.error("");
        logger.error("=== MIGRATION FAILED ===");
        logger.error("");
        logger.error("Environment:");
        logger.error("  Target: {}", node.environment().id().value());

        if (sqlContent != null) {
            logger.error("");
            logger.error("SQL Content:");
            String[] lines = sqlContent.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                logger.error("  {} | {}", String.format("%3d", i + 1), lines[i]);
            }
        }

        logger.error("");
        logger.error("Error:");
        logger.error("  {}", errorMessage);

        synchronized (failures) {
            failures.add(
                    new FailureRecord(
                            node.id().value(),
                            node.name(),
                            node.environment().id().value(),
                            errorMessage));
        }
    }

    /**
     * Called when the whole run completes; on success logs a completion line (or notes that nothing
     * needed running), and on failure prints the consolidated failure summary.
     *
     * @param summary the summary of the completed run
     */
    @Override
    public void onCompleted(ExecutionSummary summary) {
        logger.lifecycle("");
        if (summary.success()) {
            if (summary.executedCount() == 0) {
                logger.lifecycle("No migrations executed. All migrations are up to date.");
            } else {
                String operationType =
                        summary.direction() == ExecutionDirection.UP ? "Migration" : "Rollback";
                String action =
                        summary.direction() == ExecutionDirection.UP ? "executed" : "rolled back";
                logger.lifecycle(
                        "{} completed successfully. {} migration{} {}.",
                        operationType,
                        summary.executedCount(),
                        summary.executedCount() == 1 ? "" : "s",
                        action);
            }
        } else {
            printFailureSummary(summary);
        }
    }

    private void printFailureSummary(ExecutionSummary summary) {
        String directionLabel = summary.direction() == ExecutionDirection.UP ? "UP" : "DOWN";
        logger.error("=== MIGRATION SUMMARY ({}) ===", directionLabel);
        logger.error("");
        logger.error("Result:    FAILED");
        logger.error(String.format("Total:    %3d nodes", summary.totalNodes()));
        logger.error(String.format("Executed: %3d nodes", summary.executedCount()));
        List<SkippedRecord> skipSnapshot = dependencySkipsSnapshot();
        logger.error(
                String.format(
                        "Skipped:  %3d nodes%s",
                        summary.skippedCount(),
                        skipSnapshot.isEmpty() ? "" : " (incl. dependency failures)"));
        logger.error(String.format("Failed:   %3d nodes", summary.failedCount()));
        logger.error("");

        List<FailureRecord> failureSnapshot = failuresSnapshot();
        if (!failureSnapshot.isEmpty()) {
            logger.error("Failures:");
            for (int i = 0; i < failureSnapshot.size(); i++) {
                FailureRecord f = failureSnapshot.get(i);
                logger.error("  [{}] {} - {}", i + 1, f.id(), f.name());
                logger.error("      Environment: {}", f.environmentId());
                logger.error("      Error: {}", singleLine(f.errorMessage()));
                if (i < failureSnapshot.size() - 1) {
                    logger.error("");
                }
            }
            logger.error("");
        }

        if (!skipSnapshot.isEmpty()) {
            logger.error("Skipped due to failed dependencies:");
            int idWidth = skipSnapshot.stream().mapToInt(r -> r.id().length()).max().orElse(0);
            for (SkippedRecord s : skipSnapshot) {
                logger.error(String.format("  - %-" + idWidth + "s  (%s)", s.id(), s.reason()));
            }
        }
    }

    private List<FailureRecord> failuresSnapshot() {
        synchronized (failures) {
            return new ArrayList<>(failures);
        }
    }

    private List<SkippedRecord> dependencySkipsSnapshot() {
        synchronized (dependencySkips) {
            return new ArrayList<>(dependencySkips);
        }
    }

    private static String singleLine(String s) {
        int idx = s.indexOf('\n');
        return idx >= 0 ? s.substring(0, idx) : s;
    }

    /**
     * Captured details of a failed node, retained for the consolidated failure summary.
     *
     * @param id the node ID
     * @param name the node name
     * @param environmentId the ID of the environment the node targets
     * @param errorMessage the error message describing the failure
     */
    private record FailureRecord(
            String id, String name, String environmentId, String errorMessage) {}

    /**
     * Captured details of a node skipped because of a failed dependency, retained for the
     * consolidated failure summary.
     *
     * @param id the node ID
     * @param name the node name
     * @param reason the human-readable reason the node was skipped
     */
    private record SkippedRecord(String id, String name, String reason) {}
}
