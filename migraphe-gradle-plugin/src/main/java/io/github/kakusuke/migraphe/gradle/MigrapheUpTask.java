package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.core.config.ProjectConfig;
import io.github.kakusuke.migraphe.core.execution.DagExecutor;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.execution.ExecutionResult;
import io.github.kakusuke.migraphe.core.execution.Executor;
import io.github.kakusuke.migraphe.core.graph.ExecutionPlan;
import io.github.kakusuke.migraphe.core.graph.TopologicalSort;
import io.github.kakusuke.migraphe.core.graph.layout.ExecutionGraphView;
import java.util.List;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

/** UP（前進）マイグレーションを実行する Gradle タスク。 */
@DisableCachingByDefault(because = "migraphe タスクは副作用を伴い出力をキャッシュできない")
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

    @Option(option = "preview", description = "Show what would be executed without making changes")
    public void setDryRunOption(boolean dryRun) {
        getDryRun().set(dryRun);
    }

    @TaskAction
    public void up() {
        withExecutionContext(
                context -> {
                    NodeId targetId = null;
                    if (getTarget().isPresent()) {
                        targetId = NodeId.of(getTarget().get());
                    }

                    boolean dryRun = getDryRun().getOrElse(false);

                    if (targetId != null && context.graph().getNode(targetId).isEmpty()) {
                        throw new GradleException("Target not found: " + targetId.value());
                    }

                    HistoryRepository historyRepo = context.createHistoryRepository();
                    historyRepo.initialize();

                    GradleExecutionListener listener = new GradleExecutionListener(getLogger());
                    Executor executor = createExecutor(context, historyRepo, listener);

                    Set<NodeId> targetNodes = executor.determineTargetNodes(targetId);

                    if (targetNodes.isEmpty()) {
                        getLogger()
                                .lifecycle(
                                        "No migrations to execute. All migrations are up to date.");
                        return;
                    }

                    ExecutionPlan plan =
                            TopologicalSort.createExecutionPlanFor(context.graph(), targetNodes);
                    displayMigrationGraph(context, plan, historyRepo, dryRun);

                    if (dryRun) {
                        getLogger().lifecycle("");
                        getLogger().lifecycle("No changes made (dry run).");
                        return;
                    }

                    getLogger().lifecycle("");
                    getLogger().lifecycle("Executing migrations...");
                    getLogger().lifecycle("");

                    ExecutionResult result = executor.execute(targetNodes);
                    if (!result.success()) {
                        throw new GradleException("Migration failed.");
                    }
                });
    }

    /** 副作用のあるタスクはキャッシュしない。 */
    public MigrapheUpTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    /** 設定に基づいて Executor を作成する。 */
    private Executor createExecutor(
            ExecutionContext context,
            HistoryRepository historyRepo,
            GradleExecutionListener listener) {
        ProjectConfig projectConfig = context.config().getConfigMapping(ProjectConfig.class);
        ProjectConfig.ExecutionSection execConfig = projectConfig.execution();
        int maxParallelism = execConfig.parallel() ? execConfig.maxParallelism() : 1;
        return new DagExecutor(
                context.graph(), historyRepo, listener, ExecutionDirection.UP, maxParallelism);
    }

    private void displayMigrationGraph(
            ExecutionContext context,
            ExecutionPlan plan,
            HistoryRepository historyRepo,
            boolean dryRun) {
        String prefix = dryRun ? "[DRY RUN] " : "";
        String verb = dryRun ? "would be" : "will be";

        getLogger().lifecycle("");
        getLogger().lifecycle("{}Migrations to execute:", prefix);
        getLogger().lifecycle("");

        // プランのノードを DFS 順でフィルタ
        List<MigrationNode> sortedNodes = plan.filterNodesInOrder(context.nodes());

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
            getLogger().lifecycle(line);
        }

        getLogger().lifecycle("");
        int total = plan.totalNodes();
        getLogger().lifecycle("{} migration{} {} executed.", total, total == 1 ? "" : "s", verb);
    }
}
