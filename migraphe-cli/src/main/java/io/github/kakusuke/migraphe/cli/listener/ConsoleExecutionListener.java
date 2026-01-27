package io.github.kakusuke.migraphe.cli.listener;

import io.github.kakusuke.migraphe.api.execution.ExecutionListener;
import io.github.kakusuke.migraphe.api.execution.ExecutionPlanInfo;
import io.github.kakusuke.migraphe.api.execution.ExecutionSummary;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.cli.util.AnsiColor;
import org.jspecify.annotations.Nullable;

/** コンソール出力用の ExecutionListener 実装。 */
public final class ConsoleExecutionListener implements ExecutionListener {

    private final boolean colorEnabled;

    public ConsoleExecutionListener(boolean colorEnabled) {
        this.colorEnabled = colorEnabled;
    }

    @Override
    public void onPlanCreated(ExecutionPlanInfo plan) {
        // プラン作成時は何もしない（グラフ表示は別途行う）
    }

    @Override
    public void onNodeStarted(MigrationNode node, ExecutionDirection direction) {
        // 開始時は何もしない
    }

    @Override
    public void onNodeSucceeded(MigrationNode node, ExecutionDirection direction, long durationMs) {
        printResult("OK", node.id().value(), node.name(), durationMs, null);
    }

    @Override
    public void onNodeSkipped(MigrationNode node, ExecutionDirection direction, String reason) {
        printResult("SKIP", node.id().value(), node.name(), null, reason);
    }

    @Override
    public void onNodeFailed(
            MigrationNode node,
            ExecutionDirection direction,
            @Nullable String sqlContent,
            String errorMessage) {
        printResult("FAIL", node.id().value(), node.name(), null, null);
        printFailureDetails(node, sqlContent, errorMessage);
    }

    @Override
    public void onCompleted(ExecutionSummary summary) {
        System.out.println();
        if (summary.success()) {
            if (summary.executedCount() == 0) {
                System.out.println("No migrations executed. All migrations are up to date.");
            } else {
                String operationType =
                        summary.direction() == ExecutionDirection.UP ? "Migration" : "Rollback";
                String action =
                        summary.direction() == ExecutionDirection.UP ? "executed" : "rolled back";
                System.out.println(
                        operationType
                                + " completed successfully. "
                                + summary.executedCount()
                                + " migration"
                                + (summary.executedCount() == 1 ? "" : "s")
                                + " "
                                + action
                                + ".");
            }
        }
    }

    private void printResult(
            String status,
            String id,
            String name,
            @Nullable Long durationMs,
            @Nullable String extra) {
        String coloredStatus;
        switch (status) {
            case "OK" -> coloredStatus = colorEnabled ? AnsiColor.green("[OK]  ") : "[OK]   ";
            case "SKIP" -> coloredStatus = colorEnabled ? AnsiColor.yellow("[SKIP]") : "[SKIP] ";
            case "FAIL" -> coloredStatus = colorEnabled ? AnsiColor.red("[FAIL]") : "[FAIL] ";
            default -> coloredStatus = "[" + status + "]";
        }

        StringBuilder line = new StringBuilder();
        line.append(coloredStatus).append(" ").append(id).append(" - ").append(name);

        if (durationMs != null) {
            line.append(" (").append(durationMs).append("ms)");
        }
        if (extra != null) {
            line.append(" (").append(extra).append(")");
        }

        System.out.println(line);
    }

    private void printFailureDetails(
            MigrationNode node, @Nullable String sqlContent, String errorMsg) {
        System.out.println();
        System.out.println(
                colorEnabled
                        ? AnsiColor.red("=== MIGRATION FAILED ===")
                        : "=== MIGRATION FAILED ===");
        System.out.println();

        // 環境情報
        String envLabel = colorEnabled ? AnsiColor.cyan("Environment:") : "Environment:";
        System.out.println(envLabel);
        System.out.println("  Target: " + node.environment().id().value());
        System.out.println();

        // SQL内容
        if (sqlContent != null) {
            String sqlLabel = colorEnabled ? AnsiColor.cyan("SQL Content:") : "SQL Content:";
            System.out.println(sqlLabel);
            String[] lines = sqlContent.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String lineNum =
                        colorEnabled
                                ? AnsiColor.cyan(String.format("%3d", i + 1))
                                : String.format("%3d", i + 1);
                System.out.println("  " + lineNum + " | " + lines[i]);
            }
            System.out.println();
        }

        // エラーメッセージ
        String errorLabel = colorEnabled ? AnsiColor.red("Error:") : "Error:";
        System.out.println(errorLabel);
        System.out.println("  " + (colorEnabled ? AnsiColor.red(errorMsg) : errorMsg));
    }
}
