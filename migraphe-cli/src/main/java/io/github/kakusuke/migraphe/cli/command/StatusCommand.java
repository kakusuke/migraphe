package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.cli.ExecutionContext;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;

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
            HistoryRepository historyRepo = getHistoryRepository();
            historyRepo.initialize();

            // 全ノードの状態を表示
            int executedCount = 0;
            int pendingCount = 0;

            for (MigrationNode node : context.nodes()) {
                boolean executed = historyRepo.wasExecuted(node.id(), node.environment().id());

                String status = executed ? "[✓]" : "[ ]";
                System.out.println(status + " " + node.id().value() + " - " + node.name());

                if (executed) {
                    executedCount++;
                } else {
                    pendingCount++;
                }
            }

            System.out.println();
            System.out.println("Summary:");
            System.out.println("  Total: " + (executedCount + pendingCount));
            System.out.println("  Executed: " + executedCount);
            System.out.println("  Pending: " + pendingCount);

            return 0; // 成功

        } catch (Exception e) {
            System.err.println("Failed to get migration status: " + e.getMessage());
            e.printStackTrace();
            return 1; // エラー終了
        }
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
