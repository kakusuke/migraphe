package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.graph.ExecutionGraphView;
import io.github.kakusuke.migraphe.core.graph.FormatUtils;
import io.github.kakusuke.migraphe.core.graph.NodeLineInfo;
import java.util.ArrayList;
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

            // トポロジカル順序のノードリスト（context.nodes() は既にソート済み）
            List<MigrationNode> sortedNodes = new ArrayList<>(context.nodes());

            // グラフをレンダリング
            ExecutionGraphView graphView = new ExecutionGraphView(sortedNodes, false);
            List<NodeLineInfo> lines = graphView.lines();

            int executedCount = 0;
            int pendingCount = 0;

            for (int i = 0; i < lines.size(); i++) {
                NodeLineInfo info = lines.get(i);
                MigrationNode node = info.node();
                boolean executed = historyRepo.wasExecuted(node.id(), node.environment().id());

                // マージ行があれば表示
                if (info.mergeLine() != null) {
                    System.out.println(info.mergeLine());
                }

                // ノード行を表示（実行情報も同じ行に）
                String status = executed ? "[✓]" : "[ ]";
                StringBuilder nodeLineBuilder = new StringBuilder();
                nodeLineBuilder
                        .append(info.graphPrefix())
                        .append(" ")
                        .append(status)
                        .append(" ")
                        .append(node.id().value())
                        .append(" - ")
                        .append(node.name());

                if (executed) {
                    executedCount++;
                    // 実行済みノードには実行日時と所要時間を同じ行に表示
                    ExecutionRecord record =
                            historyRepo.findLatestRecord(node.id(), node.environment().id());
                    if (record != null) {
                        nodeLineBuilder
                                .append(" (")
                                .append(FormatUtils.formatDuration(record.durationMs()))
                                .append(", ")
                                .append(FormatUtils.formatDateTime(record.executedAt()))
                                .append(")");
                    }
                } else {
                    pendingCount++;
                }

                System.out.println(nodeLineBuilder);

                // 分岐行を表示（複数の子がある場合）
                if (info.branchLine() != null) {
                    System.out.println(info.branchLine());
                }

                // 接続線を表示
                if (info.connectorLine() != null) {
                    System.out.println(info.connectorLine());
                }
            }

            System.out.println();

            // サマリー
            int total = executedCount + pendingCount;
            System.out.println(
                    "Summary: Total: "
                            + total
                            + " | Executed: "
                            + executedCount
                            + " | Pending: "
                            + pendingCount);

            return 0; // 成功

        } catch (Exception e) {
            System.err.println("Failed to get migration status: " + e.getMessage());
            e.printStackTrace();
            return 1; // エラー終了
        }
    }
}
