package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import io.github.kakusuke.migraphe.cli.ExecutionContext;
import io.github.kakusuke.migraphe.core.graph.ExecutionLevel;
import io.github.kakusuke.migraphe.core.graph.ExecutionPlan;
import io.github.kakusuke.migraphe.core.graph.TopologicalSort;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;

/** UP（前進）マイグレーションを実行するコマンド。 */
public class UpCommand implements Command {

    private final ExecutionContext context;

    public UpCommand(ExecutionContext context) {
        this.context = context;
    }

    @Override
    public int execute() {
        try {
            System.out.println("Executing migrations...");
            System.out.println();

            // 1. ExecutionPlan を取得
            ExecutionPlan plan = TopologicalSort.createExecutionPlan(context.graph());

            // 2. HistoryRepository を取得（プロジェクト設定から）
            HistoryRepository historyRepo = getHistoryRepository();
            historyRepo.initialize();

            // 3. 実行計画を表示
            displayExecutionPlan(plan);

            // 4. レベルごとに実行
            int totalExecuted = 0;
            for (ExecutionLevel level : plan.levels()) {
                System.out.println("Level " + level.levelNumber() + ":");

                for (MigrationNode node : level.nodes()) {
                    // 既に実行済みかチェック
                    if (historyRepo.wasExecuted(node.id(), node.environment().id())) {
                        System.out.println("  [SKIP] " + node.name() + " (already executed)");
                        continue;
                    }

                    // 実行
                    System.out.print("  [RUN]  " + node.name() + " ... ");
                    long startTime = System.currentTimeMillis();

                    Result<TaskResult, String> result = node.upTask().execute();
                    long duration = System.currentTimeMillis() - startTime;

                    if (result.isOk()) {
                        System.out.println("OK (" + duration + "ms)");

                        // 実行記録を保存
                        TaskResult taskResult = result.value();
                        String serializedDownTask =
                                taskResult != null ? taskResult.serializedDownTask() : null;

                        ExecutionRecord record =
                                ExecutionRecord.upSuccess(
                                        node.id(),
                                        node.environment().id(),
                                        node.name(),
                                        serializedDownTask,
                                        duration);
                        historyRepo.record(record);

                        totalExecuted++;
                    } else {
                        System.out.println("FAILED");
                        String errorMsg = result.error();
                        System.err.println("  Error: " + errorMsg);

                        // 失敗記録を保存
                        ExecutionRecord failureRecord =
                                ExecutionRecord.failure(
                                        node.id(),
                                        node.environment().id(),
                                        ExecutionDirection.UP,
                                        node.name(),
                                        errorMsg != null ? errorMsg : "Unknown error");
                        historyRepo.record(failureRecord);

                        return 1; // エラー終了
                    }
                }

                System.out.println();
            }

            if (totalExecuted == 0) {
                System.out.println("No migrations to execute. All migrations are up to date.");
            } else {
                System.out.println(
                        "Migration completed successfully. "
                                + totalExecuted
                                + " migrations executed.");
            }

            return 0; // 成功

        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
            return 1; // エラー終了
        }
    }

    /** 実行計画を表示する。 */
    private void displayExecutionPlan(ExecutionPlan plan) {
        System.out.println("Execution Plan:");
        System.out.println("  Levels: " + plan.levels().size());
        System.out.println(
                "  Total Tasks: "
                        + plan.levels().stream().mapToInt(level -> level.nodes().size()).sum());
        System.out.println();
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
