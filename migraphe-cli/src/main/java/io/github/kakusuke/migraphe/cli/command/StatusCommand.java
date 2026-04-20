package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.graph.FormatUtils;
import io.github.kakusuke.migraphe.core.graph.layout.ExecutionGraphView;
import java.util.List;

/** マイグレーションの実行状況を表示するコマンド。 */
public class StatusCommand implements Command {

    private final ExecutionContext context;

    public StatusCommand(ExecutionContext context) {
        this.context = context;
    }

    @Override
    public int execute() {
        try {
            System.out.println("Migration Status");
            System.out.println("================");
            System.out.println();

            // HistoryRepository を取得
            HistoryRepository historyRepo = context.createHistoryRepository();
            historyRepo.initialize();

            // グラフをレンダリング
            ExecutionGraphView graphView = new ExecutionGraphView(context.graph(), false);

            int[] executedCount = {0};
            int[] pendingCount = {0};

            List<String> lines =
                    graphView.renderLines(
                            node -> {
                                boolean executed =
                                        historyRepo.wasExecuted(node.id(), node.environment().id());
                                StringBuilder sb = new StringBuilder();
                                if (executed) {
                                    executedCount[0]++;
                                    sb.append("[✓] ");
                                } else {
                                    pendingCount[0]++;
                                    sb.append("[ ] ");
                                }
                                sb.append(node.id().value()).append(" - ").append(node.name());
                                if (executed) {
                                    ExecutionRecord record =
                                            historyRepo.findLatestRecord(
                                                    node.id(), node.environment().id());
                                    if (record != null) {
                                        sb.append(" (")
                                                .append(
                                                        FormatUtils.formatDuration(
                                                                record.durationMs()))
                                                .append(", ")
                                                .append(
                                                        FormatUtils.formatDateTime(
                                                                record.executedAt()))
                                                .append(")");
                                    }
                                }
                                return sb.toString();
                            });

            for (String line : lines) {
                System.out.println(line);
            }

            System.out.println();

            // サマリー
            int total = executedCount[0] + pendingCount[0];
            System.out.println(
                    "Summary: Total: "
                            + total
                            + " | Executed: "
                            + executedCount[0]
                            + " | Pending: "
                            + pendingCount[0]);

            return 0; // 成功

        } catch (Exception e) {
            System.err.println("Failed to get migration status: " + e.getMessage());
            e.printStackTrace();
            return 1; // エラー終了
        }
    }
}
