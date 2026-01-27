package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.cli.listener.ConsoleExecutionListener;
import io.github.kakusuke.migraphe.cli.util.AnsiColor;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.execution.ExecutionResult;
import io.github.kakusuke.migraphe.core.execution.RollbackExecutor;
import io.github.kakusuke.migraphe.core.graph.ExecutionGraphView;
import io.github.kakusuke.migraphe.core.graph.ExecutionLevel;
import io.github.kakusuke.migraphe.core.graph.ExecutionPlan;
import io.github.kakusuke.migraphe.core.graph.NodeLineInfo;
import io.github.kakusuke.migraphe.core.graph.TopologicalSort;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
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

            // 3. Executor と Listener を作成
            ConsoleExecutionListener listener = new ConsoleExecutionListener(colorEnabled);
            RollbackExecutor executor =
                    new RollbackExecutor(context.graph(), historyRepo, listener);

            // 4. ロールバック対象ノードを決定
            Set<NodeId> targetNodes =
                    executor.determineRollbackTargets(targetVersion, allMigrations);

            if (targetNodes.isEmpty()) {
                System.out.println("No migrations to rollback.");
                return 0;
            }

            // 5. 逆順実行プラン生成してグラフ表示
            ExecutionPlan plan =
                    TopologicalSort.createReverseExecutionPlanFor(context.graph(), targetNodes);
            displayRollbackPlan(plan, historyRepo);

            // 6. dry-run の場合はここで終了
            if (dryRun) {
                System.out.println();
                System.out.println("No changes made (dry run).");
                return 0;
            }

            // 7. 確認プロンプト（-y でスキップ）
            if (!skipConfirmation && !confirmRollback()) {
                System.out.println("Rollback cancelled.");
                return 0;
            }

            // 8. ロールバック実行
            System.out.println();
            System.out.println("Executing rollback...");
            System.out.println();

            ExecutionResult result = executor.execute(targetNodes);
            return result.success() ? 0 : 1;

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

        // ExecutionGraphView を使用してグラフ表示（逆順モード）
        ExecutionGraphView graphView = new ExecutionGraphView(sortedNodes, true);
        List<NodeLineInfo> lines = graphView.lines();

        for (NodeLineInfo info : lines) {
            MigrationNode node = info.node();
            boolean executed = historyRepo.wasExecuted(node.id(), node.environment().id());

            // マージ行
            if (info.mergeLine() != null) {
                System.out.println(info.mergeLine());
            }

            // ノード行
            String status = executed ? "[✓]" : "[ ]";
            String line =
                    info.graphPrefix()
                            + " "
                            + status
                            + " "
                            + node.id().value()
                            + " - "
                            + node.name();
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
