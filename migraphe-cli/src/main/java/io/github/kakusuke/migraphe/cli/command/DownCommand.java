package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import io.github.kakusuke.migraphe.cli.ExecutionContext;
import io.github.kakusuke.migraphe.core.graph.ExecutionLevel;
import io.github.kakusuke.migraphe.core.graph.ExecutionPlan;
import io.github.kakusuke.migraphe.core.graph.TopologicalSort;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** DOWN（ロールバック）マイグレーションを実行するコマンド。 */
public class DownCommand implements Command {

    private final ExecutionContext context;
    private final @Nullable NodeId targetVersion;
    private final boolean allMigrations;
    private final boolean skipConfirmation;
    private final boolean dryRun;
    private final InputStream inputStream;

    public DownCommand(
            ExecutionContext context,
            @Nullable NodeId targetVersion,
            boolean allMigrations,
            boolean skipConfirmation,
            boolean dryRun) {
        this(context, targetVersion, allMigrations, skipConfirmation, dryRun, System.in);
    }

    /** テスト用コンストラクタ。 */
    public DownCommand(
            ExecutionContext context,
            @Nullable NodeId targetVersion,
            boolean allMigrations,
            boolean skipConfirmation,
            boolean dryRun,
            InputStream inputStream) {
        this.context = context;
        this.targetVersion = targetVersion;
        this.allMigrations = allMigrations;
        this.skipConfirmation = skipConfirmation;
        this.dryRun = dryRun;
        this.inputStream = inputStream;
    }

    @Override
    public int execute() {
        try {
            // 1. 引数のバリデーション
            if (!allMigrations) {
                if (targetVersion == null) {
                    System.err.println("Error: Either --all or target version must be specified.");
                    return 1;
                }
                if (context.graph().getNode(targetVersion).isEmpty()) {
                    System.err.println("Error: Target version not found: " + targetVersion.value());
                    return 1;
                }
            }

            // 2. HistoryRepository を取得
            HistoryRepository historyRepo = getHistoryRepository();
            historyRepo.initialize();

            // 3. ロールバック対象ノードを取得
            Set<NodeId> targetNodeIds;
            if (allMigrations) {
                // --all: 全ノードが対象
                targetNodeIds =
                        context.graph().allNodes().stream()
                                .map(MigrationNode::id)
                                .collect(Collectors.toSet());
            } else {
                // 通常: ターゲットに依存する全ノードを取得
                // targetVersion は上記のバリデーションで null でないことが保証
                targetNodeIds =
                        context.graph()
                                .getAllDependents(java.util.Objects.requireNonNull(targetVersion));
            }

            // 4. 実行済みのノードのみフィルタ
            Set<NodeId> executedDependents =
                    targetNodeIds.stream()
                            .filter(
                                    id -> {
                                        MigrationNode node =
                                                context.graph().getNode(id).orElse(null);
                                        if (node == null) return false;
                                        return historyRepo.wasExecuted(id, node.environment().id());
                                    })
                            .collect(Collectors.toSet());

            if (executedDependents.isEmpty()) {
                System.out.println("No migrations to rollback.");
                return 0;
            }

            // 5. 逆順実行プラン生成
            ExecutionPlan plan =
                    TopologicalSort.createReverseExecutionPlanFor(
                            context.graph(), executedDependents);

            // 6. ロールバック対象を表示
            displayRollbackPlan(plan);

            // 7. dry-run の場合はここで終了
            if (dryRun) {
                System.out.println();
                System.out.println("No changes made (dry run).");
                return 0;
            }

            // 8. 確認プロンプト（-y でスキップ）
            if (!skipConfirmation && !confirmRollback()) {
                System.out.println("Rollback cancelled.");
                return 0;
            }

            // 9. 各ノードをロールバック
            System.out.println();
            System.out.println("Rolling back...");

            int totalRolledBack = 0;
            for (ExecutionLevel level : plan.levels()) {
                for (MigrationNode node : level.nodes()) {
                    // 履歴から serializedDownTask を取得
                    ExecutionRecord record =
                            historyRepo.findLatestRecord(node.id(), node.environment().id());

                    if (record == null) {
                        System.out.println(
                                "  [SKIP] " + node.name() + " (no execution record found)");
                        continue;
                    }

                    // DOWN タスクを取得
                    Task downTask = getDownTask(node);
                    if (downTask == null) {
                        System.out.println("  [SKIP] " + node.name() + " (no down task available)");
                        continue;
                    }

                    // DOWN タスクを実行
                    System.out.print("  [DOWN] " + node.name() + " ... ");
                    long startTime = System.currentTimeMillis();

                    Result<TaskResult, String> result = downTask.execute();
                    long duration = System.currentTimeMillis() - startTime;

                    if (result.isOk()) {
                        System.out.println("OK (" + duration + "ms)");

                        // DOWN 実行記録を保存
                        ExecutionRecord downRecord =
                                ExecutionRecord.downSuccess(
                                        node.id(), node.environment().id(), node.name(), duration);
                        historyRepo.record(downRecord);

                        totalRolledBack++;
                    } else {
                        System.out.println("FAILED");
                        String errorMsg = result.error();
                        System.err.println("  Error: " + errorMsg);
                        return 1;
                    }
                }
            }

            System.out.println();
            System.out.println(
                    "Rollback complete. " + totalRolledBack + " migrations rolled back.");

            return 0;

        } catch (Exception e) {
            System.err.println("Rollback failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    /** ロールバック対象を表示する。 */
    private void displayRollbackPlan(ExecutionPlan plan) {
        String prefix = dryRun ? "[DRY RUN] " : "";
        String verb = dryRun ? "would be" : "will be";

        System.out.println();
        System.out.println(prefix + "The following migrations " + verb + " rolled back:");

        for (ExecutionLevel level : plan.levels()) {
            for (MigrationNode node : level.nodes()) {
                System.out.println("  - " + node.id().value() + ": " + node.name());
            }
        }

        System.out.println();
        if (allMigrations) {
            System.out.println("Rolling back all migrations.");
        } else {
            // allMigrations == false の場合、targetVersion は null でない
            NodeId targetId = java.util.Objects.requireNonNull(targetVersion);
            MigrationNode target = context.graph().getNode(targetId).orElseThrow();
            System.out.println("Target version: " + targetId.value() + " (" + target.name() + ")");
        }
    }

    /** 確認プロンプトを表示する。 */
    private boolean confirmRollback() {
        System.out.println();
        System.out.print("Proceed with rollback? [y/N]: ");
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
            String input = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
            return "y".equals(input) || "yes".equals(input);
        }
    }

    /** ノードから DOWN タスクを取得する。 */
    private @Nullable Task getDownTask(MigrationNode node) {
        // node の downTask() を使用
        return node.downTask();
    }

    /** HistoryRepository をプラグイン経由で取得する。 */
    private HistoryRepository getHistoryRepository() {
        String historyTarget =
                context.config()
                        .getConfigMapping(
                                io.github.kakusuke.migraphe.core.config.ProjectConfig.class)
                        .history()
                        .target();

        Environment historyEnv = context.environments().get(historyTarget);

        if (historyEnv == null) {
            System.out.println(
                    "Warning: History target not found. Using in-memory history repository.");
            return new InMemoryHistoryRepository();
        }

        String type = context.config().getValue("target." + historyTarget + ".type", String.class);

        MigraphePlugin<?> plugin = context.pluginRegistry().getRequiredPlugin(type);

        return plugin.historyRepositoryProvider().createRepository(historyEnv);
    }
}
