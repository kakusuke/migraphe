package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.execution.ExecutionResult;
import io.github.kakusuke.migraphe.core.execution.RollbackExecutor;
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

/** DOWN（ロールバック）マイグレーションを実行する Gradle タスク。 */
public abstract class MigrapheDownTask extends AbstractMigrapheTask {

    /** ターゲットノード ID。 */
    @Input
    @Optional
    public abstract Property<String> getTarget();

    /** 全マイグレーションをロールバック。 */
    @Input
    @Optional
    public abstract Property<Boolean> getAll();

    /** dry-run モード。 */
    @Input
    @Optional
    public abstract Property<Boolean> getDryRun();

    @Option(option = "target", description = "Target node ID to rollback to")
    public void setTargetOption(String target) {
        getTarget().set(target);
    }

    @Option(option = "all", description = "Rollback all executed migrations")
    public void setAllOption(boolean all) {
        getAll().set(all);
    }

    @Option(
            option = "preview",
            description = "Show what would be rolled back without making changes")
    public void setDryRunOption(boolean dryRun) {
        getDryRun().set(dryRun);
    }

    @TaskAction
    public void down() {
        withExecutionContext(
                context -> {
                    NodeId targetVersion = null;
                    if (getTarget().isPresent()) {
                        targetVersion = NodeId.of(getTarget().get());
                    }

                    boolean allMigrations = getAll().getOrElse(false);
                    boolean dryRun = getDryRun().getOrElse(false);

                    if (!allMigrations && targetVersion == null) {
                        throw new GradleException(
                                "Either --all or --target must be specified.\n"
                                        + "Usage:\n"
                                        + "  ./gradlew migrapheDown --all\n"
                                        + "  ./gradlew migrapheDown --target=<nodeId>");
                    }

                    if (targetVersion != null && context.graph().getNode(targetVersion).isEmpty()) {
                        throw new GradleException(
                                "Target version not found: " + targetVersion.value());
                    }

                    HistoryRepository historyRepo = context.createHistoryRepository();
                    historyRepo.initialize();

                    GradleExecutionListener listener = new GradleExecutionListener(getLogger());
                    RollbackExecutor executor =
                            new RollbackExecutor(context.graph(), historyRepo, listener);

                    Set<NodeId> targetNodes =
                            executor.determineRollbackTargets(targetVersion, allMigrations);

                    if (targetNodes.isEmpty()) {
                        getLogger().lifecycle("No migrations to rollback.");
                        return;
                    }

                    ExecutionPlan plan =
                            TopologicalSort.createReverseExecutionPlanFor(
                                    context.graph(), targetNodes);
                    displayRollbackPlan(context, plan, historyRepo, dryRun);

                    if (dryRun) {
                        getLogger().lifecycle("");
                        getLogger().lifecycle("No changes made (dry run).");
                        return;
                    }

                    getLogger().lifecycle("");
                    getLogger().lifecycle("Executing rollback...");
                    getLogger().lifecycle("");

                    ExecutionResult result = executor.execute(targetNodes);
                    if (!result.success()) {
                        throw new GradleException("Rollback failed.");
                    }
                });
    }

    /** 副作用のあるタスクはキャッシュしない。 */
    public MigrapheDownTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    private void displayRollbackPlan(
            ExecutionContext context,
            ExecutionPlan plan,
            HistoryRepository historyRepo,
            boolean dryRun) {
        String prefix = dryRun ? "[DRY RUN] " : "";
        String verb = dryRun ? "would be" : "will be";

        getLogger().lifecycle("");
        getLogger().lifecycle("{}Migrations to rollback:", prefix);
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
        getLogger().lifecycle("{} migration{} {} rolled back.", total, total == 1 ? "" : "s", verb);
    }
}
