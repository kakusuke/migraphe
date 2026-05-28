package io.github.kakusuke.migraphe.cli.listener;

import io.github.kakusuke.migraphe.api.execution.ExecutionListener;
import io.github.kakusuke.migraphe.api.execution.ExecutionPlanInfo;
import io.github.kakusuke.migraphe.api.execution.ExecutionSummary;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.cli.util.AnsiColor;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** コンソール出力用の ExecutionListener 実装。 */
public final class ConsoleExecutionListener implements ExecutionListener {

    private final boolean colorEnabled;
    private final List<FailureRecord> failures = new ArrayList<>();
    private final List<SkippedRecord> dependencySkips = new ArrayList<>();

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
        if (reason.startsWith("dependency failed:")) {
            synchronized (dependencySkips) {
                dependencySkips.add(new SkippedRecord(node.id().value(), node.name(), reason));
            }
        }
    }

    @Override
    public void onNodeFailed(
            MigrationNode node,
            ExecutionDirection direction,
            @Nullable String sqlContent,
            String errorMessage) {
        printResult("FAIL", node.id().value(), node.name(), null, null);
        printFailureDetails(node, sqlContent, errorMessage);
        synchronized (failures) {
            failures.add(
                    new FailureRecord(
                            node.id().value(),
                            node.name(),
                            node.environment().id().value(),
                            errorMessage));
        }
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
        } else {
            printFailureSummary(summary);
        }
    }

    private void printFailureSummary(ExecutionSummary summary) {
        String directionLabel = summary.direction() == ExecutionDirection.UP ? "UP" : "DOWN";
        String header = "=== MIGRATION SUMMARY (" + directionLabel + ") ===";
        System.out.println(colorEnabled ? AnsiColor.red(header) : header);
        System.out.println();

        String resultLabel = colorEnabled ? AnsiColor.red("FAILED") : "FAILED";
        System.out.println("Result:    " + resultLabel);
        System.out.printf("Total:    %3d nodes%n", summary.totalNodes());
        System.out.printf("Executed: %3d nodes%n", summary.executedCount());
        System.out.printf(
                "Skipped:  %3d nodes%s%n",
                summary.skippedCount(),
                dependencySkipsSnapshot().isEmpty() ? "" : " (incl. dependency failures)");
        System.out.printf("Failed:   %3d nodes%n", summary.failedCount());
        System.out.println();

        List<FailureRecord> failureSnapshot = failuresSnapshot();
        if (!failureSnapshot.isEmpty()) {
            String failuresLabel = colorEnabled ? AnsiColor.red("Failures:") : "Failures:";
            System.out.println(failuresLabel);
            for (int i = 0; i < failureSnapshot.size(); i++) {
                FailureRecord f = failureSnapshot.get(i);
                System.out.println("  [" + (i + 1) + "] " + f.id() + " - " + f.name());
                System.out.println("      Environment: " + f.environmentId());
                System.out.println(
                        "      Error: "
                                + (colorEnabled
                                        ? AnsiColor.red(singleLine(f.errorMessage()))
                                        : singleLine(f.errorMessage())));
                if (i < failureSnapshot.size() - 1) {
                    System.out.println();
                }
            }
            System.out.println();
        }

        List<SkippedRecord> skipSnapshot = dependencySkipsSnapshot();
        if (!skipSnapshot.isEmpty()) {
            String skipLabel =
                    colorEnabled
                            ? AnsiColor.yellow("Skipped due to failed dependencies:")
                            : "Skipped due to failed dependencies:";
            System.out.println(skipLabel);
            int idWidth = skipSnapshot.stream().mapToInt(r -> r.id().length()).max().orElse(0);
            for (SkippedRecord s : skipSnapshot) {
                System.out.printf("  - %-" + idWidth + "s  (%s)%n", s.id(), s.reason());
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

    private record FailureRecord(
            String id, String name, String environmentId, String errorMessage) {}

    private record SkippedRecord(String id, String name, String reason) {}
}
