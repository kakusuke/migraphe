package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.cli.listener.ConsoleExecutionListener;
import io.github.kakusuke.migraphe.cli.util.AnsiColor;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.execution.ExecutionResult;
import io.github.kakusuke.migraphe.core.execution.MigrationExecutor;
import io.github.kakusuke.migraphe.core.graph.ExecutionGraphView;
import io.github.kakusuke.migraphe.core.graph.ExecutionPlan;
import io.github.kakusuke.migraphe.core.graph.TopologicalSort;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
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
            HistoryRepository historyRepo = context.createHistoryRepository();
            historyRepo.initialize();

            // 3. Executor と Listener を作成
            ConsoleExecutionListener listener = new ConsoleExecutionListener(colorEnabled);
            MigrationExecutor executor =
                    new MigrationExecutor(context.graph(), historyRepo, listener);

            // 4. 実行対象ノードを決定
            Set<NodeId> targetNodes = executor.determineTargetNodes(targetId);

            if (targetNodes.isEmpty()) {
                System.out.println("No migrations to execute. All migrations are up to date.");
                return 0;
            }

            // 5. ExecutionPlan を生成してグラフ表示
            ExecutionPlan plan =
                    TopologicalSort.createExecutionPlanFor(context.graph(), targetNodes);
            displayMigrationGraph(context, plan, historyRepo);

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

            ExecutionResult result = executor.execute(targetNodes);
            return result.success() ? 0 : 1;

        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    /** マイグレーショングラフを表示する。 */
    private void displayMigrationGraph(
            ExecutionContext context, ExecutionPlan plan, HistoryRepository historyRepo) {
        String prefix = dryRun ? "[DRY RUN] " : "";
        String verb = dryRun ? "would be" : "will be";

        System.out.println();
        System.out.println(prefix + "Migrations to execute:");
        System.out.println();

        // プランのノードを DFS 順でフィルタ
        List<MigrationNode> sortedNodes = plan.filterNodesInOrder(context.nodes());

        // ExecutionGraphView を使用してグラフ表示
        ExecutionGraphView graphView = new ExecutionGraphView(sortedNodes);
        List<String> lines =
                graphView.renderLines(
                        node -> {
                            String status =
                                    historyRepo.wasExecuted(node.id(), node.environment().id())
                                            ? "[✓]"
                                            : "[ ]";
                            return status + " " + node.id().value() + " - " + node.name();
                        });
        for (String line : lines) {
            System.out.println(line);
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
}
