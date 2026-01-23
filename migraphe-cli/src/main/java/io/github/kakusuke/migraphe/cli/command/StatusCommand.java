package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.cli.ExecutionContext;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** マイグレーションの実行状況を表示するコマンド。 */
public class StatusCommand implements Command {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

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
            HistoryRepository historyRepo = getHistoryRepository();
            historyRepo.initialize();

            // トポロジカル順序のノードリスト（context.nodes() は既にソート済み）
            List<MigrationNode> sortedNodes = new ArrayList<>(context.nodes());

            // グラフをレンダリング
            GraphRenderer renderer = new GraphRenderer(sortedNodes);
            List<GraphRenderer.NodeGraphInfo> graphInfos = renderer.render();

            int executedCount = 0;
            int pendingCount = 0;

            for (int i = 0; i < graphInfos.size(); i++) {
                GraphRenderer.NodeGraphInfo info = graphInfos.get(i);
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
                        .append(info.nodeLine())
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
                                .append(formatDuration(record.durationMs()))
                                .append(", ")
                                .append(formatDateTime(record.executedAt()))
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

    /** 日時をフォーマットする。 */
    private String formatDateTime(Instant instant) {
        return DATE_TIME_FORMATTER.format(instant);
    }

    /** 所要時間をフォーマットする。 */
    private String formatDuration(long durationMs) {
        if (durationMs >= 1000) {
            return String.format("%.1fs", durationMs / 1000.0);
        }
        return durationMs + "ms";
    }

    /** HistoryRepository をプラグイン経由で取得する。 */
    private HistoryRepository getHistoryRepository() {
        // プロジェクト設定から history.target を取得
        String historyTarget =
                context.config()
                        .getConfigMapping(
                                io.github.kakusuke.migraphe.core.config.ProjectConfig.class)
                        .history()
                        .target();

        Environment historyEnv = context.environments().get(historyTarget);

        if (historyEnv == null) {
            // フォールバック: InMemoryHistoryRepository を使用
            System.out.println(
                    "Warning: History target not found. Using in-memory history repository.");
            return new InMemoryHistoryRepository();
        }

        // history.target の type を取得してプラグインを特定
        String type = context.config().getValue("target." + historyTarget + ".type", String.class);

        MigraphePlugin<?> plugin = context.pluginRegistry().getRequiredPlugin(type);

        // プラグインの HistoryRepositoryProvider で HistoryRepository を生成
        return plugin.historyRepositoryProvider().createRepository(historyEnv);
    }
}
