package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.core.execution.DagExecutor;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.execution.ExecutionResult;
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

/**
 * Gradle task that runs rollback (DOWN) migrations.
 *
 * <p>Registered as {@code migrapheDown} by {@link MigrapheGradlePlugin}, the task rolls back
 * previously executed migrations, either {@linkplain #getAll() all of them} or down to a given
 * {@linkplain #getTarget() target node}. It prints the rollback plan and — unless running in
 * {@linkplain #getDryRun() dry-run} mode — executes it via a {@link DagExecutor} in the {@link
 * ExecutionDirection#DOWN} direction. Rollback always runs with a parallelism of 1.
 *
 * <p>The task fails the build with a {@link GradleException} when neither {@code --all} nor {@code
 * --target} is given, when the target node is unknown, or when any rollback fails.
 */
@DisableCachingByDefault(
        because = "Migraphe tasks have side effects and their output cannot be cached")
public abstract class MigrapheDownTask extends AbstractMigrapheTask {

    /**
     * Returns the optional target node ID to roll back to.
     *
     * @return the optional target node ID property
     */
    @Input
    @Optional
    public abstract Property<String> getTarget();

    /**
     * Returns the optional flag requesting rollback of all executed migrations.
     *
     * @return the optional rollback-all flag property
     */
    @Input
    @Optional
    public abstract Property<Boolean> getAll();

    /**
     * Returns the optional dry-run flag. When {@code true}, the rollback plan is printed but
     * nothing is executed.
     *
     * @return the optional dry-run flag property
     */
    @Input
    @Optional
    public abstract Property<Boolean> getDryRun();

    /**
     * Sets the {@linkplain #getTarget() target} from the {@code --target} command-line option.
     *
     * @param target the target node ID to roll back to
     */
    @Option(option = "target", description = "Target node ID to rollback to")
    public void setTargetOption(String target) {
        getTarget().set(target);
    }

    /**
     * Enables rollback of {@linkplain #getAll() all} executed migrations from the {@code --all}
     * command-line option.
     *
     * @param all {@code true} to roll back all executed migrations
     */
    @Option(option = "all", description = "Rollback all executed migrations")
    public void setAllOption(boolean all) {
        getAll().set(all);
    }

    /**
     * Enables {@linkplain #getDryRun() dry-run} mode from the {@code --preview} command-line
     * option.
     *
     * @param dryRun {@code true} to preview the rollback without executing it
     */
    @Option(
            option = "preview",
            description = "Show what would be rolled back without making changes")
    public void setDryRunOption(boolean dryRun) {
        getDryRun().set(dryRun);
    }

    /**
     * Task action that executes the rollback migrations.
     *
     * <p>Loads the execution context, determines the rollback targets (all, or down to the target),
     * prints the plan, and — unless in dry-run mode — runs the rollback. Initializes the history
     * repository before execution.
     *
     * @throws GradleException if neither {@code --all} nor {@code --target} is specified, if the
     *     target version is not found, or if any rollback fails
     */
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
                    DagExecutor executor =
                            new DagExecutor(
                                    context.graph(),
                                    historyRepo,
                                    listener,
                                    ExecutionDirection.DOWN,
                                    1);

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

    /** Creates the task and marks it as never up to date, since it has side effects. */
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

        // Filter the plan's nodes in DFS order.
        List<MigrationNode> sortedNodes = plan.filterNodesInOrder(context.nodes());

        ExecutionGraphView graphView = new ExecutionGraphView(sortedNodes, true);
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
