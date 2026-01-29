package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.execution.ExecutionResult;
import io.github.kakusuke.migraphe.core.execution.MigrationExecutor;
import io.github.kakusuke.migraphe.core.graph.ExecutionGraphView;
import io.github.kakusuke.migraphe.core.graph.ExecutionLevel;
import io.github.kakusuke.migraphe.core.graph.ExecutionPlan;
import io.github.kakusuke.migraphe.core.graph.NodeLineInfo;
import io.github.kakusuke.migraphe.core.graph.TopologicalSort;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/** UP（前進）マイグレーションを実行する Gradle タスク。 */
public abstract class MigrapheUpTask extends AbstractMigrapheTask {

    /** ターゲットノード ID。 */
    @Input
    @Optional
    public abstract Property<String> getTarget();

    /** dry-run モード。 */
    @Input
    @Optional
    public abstract Property<Boolean> getDryRun();

    @Option(option = "target", description = "Target node ID to migrate up to")
    public void setTargetOption(String target) {
        getTarget().set(target);
    }

    @Option(option = "dry-run", description = "Show what would be executed without making changes")
    public void setDryRunOption(boolean dryRun) {
        getDryRun().set(dryRun);
    }

    @TaskAction
    public void up() {
        ExecutionContext context = loadExecutionContext();

        NodeId targetId = null;
        if (getTarget().isPresent()) {
            targetId = NodeId.of(getTarget().get());
        } else {
            // -P フォールバック
            Object prop = getProject().findProperty("migraphe.up.target");
            if (prop != null) {
                targetId = NodeId.of(prop.toString());
            }
        }

        boolean dryRun = getDryRun().getOrElse(false);
        if (!dryRun) {
            Object prop = getProject().findProperty("migraphe.up.dryRun");
            if ("true".equals(String.valueOf(prop))) {
                dryRun = true;
            }
        }

        // ターゲット指定の場合、存在確認
        if (targetId != null && context.graph().getNode(targetId).isEmpty()) {
            throw new GradleException("Target not found: " + targetId.value());
        }

        // HistoryRepository を取得
        HistoryRepository historyRepo =
                HistoryRepositoryHelper.getHistoryRepository(context, getLogger());
        historyRepo.initialize();

        // Executor を作成
        GradleExecutionListener listener = new GradleExecutionListener(getLogger());
        MigrationExecutor executor = new MigrationExecutor(context.graph(), historyRepo, listener);

        // 実行対象ノードを決定
        Set<NodeId> targetNodes = executor.determineTargetNodes(targetId);

        if (targetNodes.isEmpty()) {
            getLogger().lifecycle("No migrations to execute. All migrations are up to date.");
            return;
        }

        // 実行プランを生成してグラフ表示
        ExecutionPlan plan = TopologicalSort.createExecutionPlanFor(context.graph(), targetNodes);
        displayMigrationGraph(plan, historyRepo, dryRun);

        // dry-run の場合はここで終了
        if (dryRun) {
            getLogger().lifecycle("");
            getLogger().lifecycle("No changes made (dry run).");
            return;
        }

        // マイグレーション実行
        getLogger().lifecycle("");
        getLogger().lifecycle("Executing migrations...");
        getLogger().lifecycle("");

        ExecutionResult result = executor.execute(targetNodes);
        if (!result.success()) {
            throw new GradleException("Migration failed.");
        }
    }

    /** 副作用のあるタスクはキャッシュしない。 */
    public MigrapheUpTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    private void displayMigrationGraph(
            ExecutionPlan plan, HistoryRepository historyRepo, boolean dryRun) {
        String prefix = dryRun ? "[DRY RUN] " : "";
        String verb = dryRun ? "would be" : "will be";

        getLogger().lifecycle("");
        getLogger().lifecycle("{}Migrations to execute:", prefix);
        getLogger().lifecycle("");

        List<MigrationNode> sortedNodes = new ArrayList<>();
        for (ExecutionLevel level : plan.levels()) {
            sortedNodes.addAll(level.nodes());
        }

        ExecutionGraphView graphView = new ExecutionGraphView(sortedNodes, false);
        List<NodeLineInfo> lines = graphView.lines();

        for (NodeLineInfo info : lines) {
            MigrationNode node = info.node();
            boolean executed = historyRepo.wasExecuted(node.id(), node.environment().id());

            if (info.mergeLine() != null) {
                getLogger().lifecycle(info.mergeLine());
            }

            String status = executed ? "[✓]" : "[ ]";
            String line =
                    info.graphPrefix()
                            + " "
                            + status
                            + " "
                            + node.id().value()
                            + " - "
                            + node.name();
            getLogger().lifecycle(line);

            if (info.branchLine() != null) {
                getLogger().lifecycle(info.branchLine());
            }

            if (info.connectorLine() != null) {
                getLogger().lifecycle(info.connectorLine());
            }
        }

        getLogger().lifecycle("");
        int total = plan.totalNodes();
        getLogger().lifecycle("{} migration{} {} executed.", total, total == 1 ? "" : "s", verb);
    }
}
