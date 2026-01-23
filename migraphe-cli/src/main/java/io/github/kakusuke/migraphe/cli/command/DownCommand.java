package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.api.task.SqlContentProvider;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import io.github.kakusuke.migraphe.cli.ExecutionContext;
import io.github.kakusuke.migraphe.cli.util.AnsiColor;
import io.github.kakusuke.migraphe.core.graph.ExecutionLevel;
import io.github.kakusuke.migraphe.core.graph.ExecutionPlan;
import io.github.kakusuke.migraphe.core.graph.TopologicalSort;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
    private final boolean colorEnabled;

    public DownCommand(
            ExecutionContext context,
            @Nullable NodeId targetVersion,
            boolean allMigrations,
            boolean skipConfirmation,
            boolean dryRun) {
        this(
                context,
                targetVersion,
                allMigrations,
                skipConfirmation,
                dryRun,
                System.in,
                AnsiColor.isColorEnabled());
    }

    /** テスト用コンストラクタ。 */
    public DownCommand(
            ExecutionContext context,
            @Nullable NodeId targetVersion,
            boolean allMigrations,
            boolean skipConfirmation,
            boolean dryRun,
            InputStream inputStream,
            boolean colorEnabled) {
        this.context = context;
        this.targetVersion = targetVersion;
        this.allMigrations = allMigrations;
        this.skipConfirmation = skipConfirmation;
        this.dryRun = dryRun;
        this.inputStream = inputStream;
        this.colorEnabled = colorEnabled;
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
                // 通常: ターゲット自身 + ターゲットに依存する全ノードを取得
                // targetVersion は上記のバリデーションで null でないことが保証
                NodeId target = java.util.Objects.requireNonNull(targetVersion);
                targetNodeIds = new java.util.HashSet<>(context.graph().getAllDependents(target));
                targetNodeIds.add(target); // ターゲット自身も含める
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
            displayRollbackPlan(plan, historyRepo);

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
            System.out.println("Executing rollback...");
            System.out.println();

            return executeRollback(plan, historyRepo);

        } catch (Exception e) {
            System.err.println("Rollback failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    /** ロールバック対象を表示する。 */
    private void displayRollbackPlan(ExecutionPlan plan, HistoryRepository historyRepo) {
        String prefix = dryRun ? "[DRY RUN] " : "";
        String verb = dryRun ? "would be" : "will be";

        System.out.println();
        System.out.println(prefix + "Migrations to rollback:");
        System.out.println();

        // トポロジカル順序でノードを取得
        List<MigrationNode> sortedNodes = new ArrayList<>();
        for (ExecutionLevel level : plan.levels()) {
            sortedNodes.addAll(level.nodes());
        }

        // GraphRenderer を使用してグラフ表示（逆順モード）
        GraphRenderer renderer = new GraphRenderer(sortedNodes, true);
        List<GraphRenderer.NodeGraphInfo> graphInfos = renderer.render();

        for (GraphRenderer.NodeGraphInfo info : graphInfos) {
            MigrationNode node = info.node();
            boolean executed = historyRepo.wasExecuted(node.id(), node.environment().id());

            // マージ行
            if (info.mergeLine() != null) {
                System.out.println(info.mergeLine());
            }

            // ノード行
            String status = executed ? "[✓]" : "[ ]";
            String line =
                    info.nodeLine() + " " + status + " " + node.id().value() + " - " + node.name();
            System.out.println(line);

            // 分岐行
            if (info.branchLine() != null) {
                System.out.println(info.branchLine());
            }

            // 接続線
            if (info.connectorLine() != null) {
                System.out.println(info.connectorLine());
            }
        }

        System.out.println();
        int total = plan.totalNodes();
        System.out.println(
                total + " migration" + (total == 1 ? "" : "s") + " " + verb + " rolled back.");
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

    /** ロールバックを実行する。 */
    private int executeRollback(ExecutionPlan plan, HistoryRepository historyRepo) {
        int totalRolledBack = 0;

        for (ExecutionLevel level : plan.levels()) {
            for (MigrationNode node : level.nodes()) {
                // 履歴から serializedDownTask を取得
                ExecutionRecord record =
                        historyRepo.findLatestRecord(node.id(), node.environment().id());

                if (record == null) {
                    printResult(
                            "SKIP", node.id().value(), node.name(), null, "no execution record");
                    continue;
                }

                // DOWN タスクを取得
                Task downTask = getDownTask(node);
                if (downTask == null) {
                    printResult("SKIP", node.id().value(), node.name(), null, "no down task");
                    continue;
                }

                // DOWN タスクを実行
                long startTime = System.currentTimeMillis();
                Result<TaskResult, String> result = downTask.execute();
                long duration = System.currentTimeMillis() - startTime;

                if (result.isOk()) {
                    printResult("OK", node.id().value(), node.name(), duration, null);

                    // DOWN 実行記録を保存
                    ExecutionRecord downRecord =
                            ExecutionRecord.downSuccess(
                                    node.id(), node.environment().id(), node.name(), duration);
                    historyRepo.record(downRecord);

                    totalRolledBack++;
                } else {
                    printResult("FAIL", node.id().value(), node.name(), duration, null);
                    String errorMsg = result.error();

                    // 失敗時の詳細表示
                    printFailureDetails(node, downTask, errorMsg);

                    // 失敗記録を保存
                    ExecutionRecord failureRecord =
                            ExecutionRecord.failure(
                                    node.id(),
                                    node.environment().id(),
                                    ExecutionDirection.DOWN,
                                    node.name(),
                                    errorMsg != null ? errorMsg : "Unknown error");
                    historyRepo.record(failureRecord);

                    return 1;
                }
            }
        }

        System.out.println();
        if (totalRolledBack == 0) {
            System.out.println("No migrations rolled back.");
        } else {
            System.out.println(
                    "Rollback completed successfully. "
                            + totalRolledBack
                            + " migration"
                            + (totalRolledBack == 1 ? "" : "s")
                            + " rolled back.");
        }

        return 0;
    }

    /** 結果を色付きで表示する。 */
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

    /** 失敗時の詳細情報を表示する。 */
    private void printFailureDetails(MigrationNode node, Task downTask, @Nullable String errorMsg) {
        System.out.println();
        System.out.println(
                colorEnabled
                        ? AnsiColor.red("=== ROLLBACK FAILED ===")
                        : "=== ROLLBACK FAILED ===");
        System.out.println();

        // 環境情報
        String envLabel = colorEnabled ? AnsiColor.cyan("Environment:") : "Environment:";
        System.out.println(envLabel);
        System.out.println("  Target: " + node.environment().id().value());
        System.out.println();

        // SQL内容（SqlContentProviderを実装している場合）
        if (downTask instanceof SqlContentProvider sqlProvider) {
            String sqlLabel = colorEnabled ? AnsiColor.cyan("SQL Content:") : "SQL Content:";
            System.out.println(sqlLabel);
            String sql = sqlProvider.sqlContent();
            String[] lines = sql.split("\n", -1);
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
        String errorContent = errorMsg != null ? errorMsg : "Unknown error";
        System.out.println("  " + (colorEnabled ? AnsiColor.red(errorContent) : errorContent));
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
