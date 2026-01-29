package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.execution.ExecutionListener;
import io.github.kakusuke.migraphe.api.execution.ExecutionPlanInfo;
import io.github.kakusuke.migraphe.api.execution.ExecutionSummary;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import org.gradle.api.logging.Logger;
import org.jspecify.annotations.Nullable;

/** Gradle の Logger API を使用する ExecutionListener 実装。 */
public final class GradleExecutionListener implements ExecutionListener {

    private final Logger logger;

    public GradleExecutionListener(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void onPlanCreated(ExecutionPlanInfo plan) {
        // プラン作成時は何もしない
    }

    @Override
    public void onNodeStarted(MigrationNode node, ExecutionDirection direction) {
        // 開始時は何もしない
    }

    @Override
    public void onNodeSucceeded(MigrationNode node, ExecutionDirection direction, long durationMs) {
        logger.lifecycle("[OK]   {} - {} ({}ms)", node.id().value(), node.name(), durationMs);
    }

    @Override
    public void onNodeSkipped(MigrationNode node, ExecutionDirection direction, String reason) {
        logger.lifecycle("[SKIP] {} - {} ({})", node.id().value(), node.name(), reason);
    }

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
    }

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
        }
    }
}
