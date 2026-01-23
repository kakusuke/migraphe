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
import java.util.*;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** UP（前進）マイグレーションを実行するコマンド。 */
public class UpCommand implements Command {

    private final ExecutionContext context;
    private final @Nullable NodeId targetId;
    private final boolean skipConfirmation;
    private final boolean dryRun;
    private final InputStream inputStream;
    private final boolean colorEnabled;

    public UpCommand(ExecutionContext context) {
        this(context, null, false, false, System.in, AnsiColor.isColorEnabled());
    }

    public UpCommand(
            ExecutionContext context,
            @Nullable NodeId targetId,
            boolean skipConfirmation,
            boolean dryRun) {
        this(context, targetId, skipConfirmation, dryRun, System.in, AnsiColor.isColorEnabled());
    }

    /** テスト用コンストラクタ。 */
    public UpCommand(
            ExecutionContext context,
            @Nullable NodeId targetId,
            boolean skipConfirmation,
            boolean dryRun,
            InputStream inputStream,
            boolean colorEnabled) {
        this.context = context;
        this.targetId = targetId;
        this.skipConfirmation = skipConfirmation;
        this.dryRun = dryRun;
        this.inputStream = inputStream;
        this.colorEnabled = colorEnabled;
    }

    @Override
    public int execute() {
        try {
            // 1. ターゲット指定の場合、ノードの存在を確認
            if (targetId != null && context.graph().getNode(targetId).isEmpty()) {
                System.err.println("Error: Target not found: " + targetId.value());
                return 1;
            }

            // 2. HistoryRepository を取得
            HistoryRepository historyRepo = getHistoryRepository();
            historyRepo.initialize();

            // 3. 実行対象ノードを決定
            Set<NodeId> targetNodes = determineTargetNodes(historyRepo);

            if (targetNodes.isEmpty()) {
                System.out.println("No migrations to execute. All migrations are up to date.");
                return 0;
            }

            // 4. ExecutionPlan を生成
            ExecutionPlan plan =
                    TopologicalSort.createExecutionPlanFor(context.graph(), targetNodes);

            // 5. グラフ表示
            displayMigrationGraph(plan, historyRepo);

            // 6. dry-run の場合はここで終了
            if (dryRun) {
                System.out.println();
                System.out.println("No changes made (dry run).");
                return 0;
            }

            // 7. 確認プロンプト（-y でスキップ）
            if (!skipConfirmation && !confirmExecution()) {
                System.out.println("Migration cancelled.");
                return 0;
            }

            // 8. マイグレーション実行
            System.out.println();
            System.out.println("Executing migrations...");
            System.out.println();

            return executeMigrations(plan, historyRepo);

        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    /** 実行対象ノードを決定する。 */
    private Set<NodeId> determineTargetNodes(HistoryRepository historyRepo) {
        Set<NodeId> candidates;

        if (targetId != null) {
            // ID指定: ターゲット + ターゲットの全依存先
            candidates = new HashSet<>(context.graph().getAllDependencies(targetId));
            candidates.add(targetId);
        } else {
            // 全体: グラフの全ノード
            candidates =
                    context.graph().allNodes().stream()
                            .map(MigrationNode::id)
                            .collect(Collectors.toSet());
        }

        // 未実行のノードのみフィルタ
        return candidates.stream()
                .filter(
                        id -> {
                            MigrationNode node = context.graph().getNode(id).orElse(null);
                            if (node == null) return false;
                            return !historyRepo.wasExecuted(id, node.environment().id());
                        })
                .collect(Collectors.toSet());
    }

    /** マイグレーショングラフを表示する。 */
    private void displayMigrationGraph(ExecutionPlan plan, HistoryRepository historyRepo) {
        String prefix = dryRun ? "[DRY RUN] " : "";
        String verb = dryRun ? "would be" : "will be";

        System.out.println();
        System.out.println(prefix + "Migrations to execute:");
        System.out.println();

        // トポロジカル順序でノードを取得
        List<MigrationNode> sortedNodes = new ArrayList<>();
        for (ExecutionLevel level : plan.levels()) {
            sortedNodes.addAll(level.nodes());
        }

        // GraphRenderer を使用してグラフ表示
        GraphRenderer renderer = new GraphRenderer(sortedNodes);
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
                total + " migration" + (total == 1 ? "" : "s") + " " + verb + " executed.");
    }

    /** 確認プロンプトを表示する。 */
    private boolean confirmExecution() {
        System.out.println();
        System.out.print("Proceed? [y/N]: ");
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
            String input = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
            return "y".equals(input) || "yes".equals(input);
        }
    }

    /** マイグレーションを実行する。 */
    private int executeMigrations(ExecutionPlan plan, HistoryRepository historyRepo) {
        int totalExecuted = 0;

        for (ExecutionLevel level : plan.levels()) {
            for (MigrationNode node : level.nodes()) {
                // 既に実行済みかチェック
                if (historyRepo.wasExecuted(node.id(), node.environment().id())) {
                    printResult("SKIP", node.id().value(), node.name(), null, "already executed");
                    continue;
                }

                // 実行
                long startTime = System.currentTimeMillis();
                Result<TaskResult, String> result = node.upTask().execute();
                long duration = System.currentTimeMillis() - startTime;

                if (result.isOk()) {
                    printResult("OK", node.id().value(), node.name(), duration, null);

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
                    printResult("FAIL", node.id().value(), node.name(), duration, null);
                    String errorMsg = result.error();

                    // 失敗時の詳細表示
                    printFailureDetails(node, errorMsg);

                    // 失敗記録を保存
                    ExecutionRecord failureRecord =
                            ExecutionRecord.failure(
                                    node.id(),
                                    node.environment().id(),
                                    ExecutionDirection.UP,
                                    node.name(),
                                    errorMsg != null ? errorMsg : "Unknown error");
                    historyRepo.record(failureRecord);

                    return 1;
                }
            }
        }

        System.out.println();
        if (totalExecuted == 0) {
            System.out.println("No migrations executed. All migrations are up to date.");
        } else {
            System.out.println(
                    "Migration completed successfully. "
                            + totalExecuted
                            + " migration"
                            + (totalExecuted == 1 ? "" : "s")
                            + " executed.");
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
    private void printFailureDetails(MigrationNode node, @Nullable String errorMsg) {
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

        // SQL内容（SqlContentProviderを実装している場合）
        Task upTask = node.upTask();
        if (upTask instanceof SqlContentProvider sqlProvider) {
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
