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
import io.github.kakusuke.migraphe.core.execution.UpBlocker;
import io.github.kakusuke.migraphe.core.execution.UpPlanFormatter;
import io.github.kakusuke.migraphe.core.execution.UpService;
import io.github.kakusuke.migraphe.core.execution.UpService.UpPlan;
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
 * Gradle task that runs forward (UP) migrations.
 *
 * <p>Registered as {@code migrapheUp} by {@link MigrapheGradlePlugin}, the task asks {@link
 * UpService} which nodes are pending (optionally bounded by a {@linkplain #getTarget() target
 * node}), prints the execution graph, and — unless running in {@linkplain #getDryRun() dry-run}
 * mode — executes the migrations via a {@link DagExecutor} in the {@link ExecutionDirection#UP}
 * direction. Parallelism is taken from the project's {@code execution} configuration.
 *
 * <p>Every check that can refuse a run lives in {@code UpService}, so the CLI and this task refuse
 * the same things in the same words.
 *
 * <p>The task fails the build with a {@link GradleException} when the target node is unknown, when
 * something refuses the run, or when any migration fails.
 */
@DisableCachingByDefault(
        because = "Migraphe tasks have side effects and their output cannot be cached")
public abstract class MigrapheUpTask extends AbstractMigrapheTask {

    /**
     * Returns the optional target node ID to migrate up to. When absent, all pending migrations are
     * executed.
     *
     * @return the optional target node ID property
     */
    @Input
    @Optional
    public abstract Property<String> getTarget();

    /**
     * Returns the optional dry-run flag. When {@code true}, the plan is printed but no migrations
     * are executed.
     *
     * @return the optional dry-run flag property
     */
    @Input
    @Optional
    public abstract Property<Boolean> getDryRun();

    /**
     * Sets the {@linkplain #getTarget() target} from the {@code --target} command-line option.
     *
     * @param target the target node ID to migrate up to
     */
    @Option(option = "target", description = "Target node ID to migrate up to")
    public void setTargetOption(String target) {
        getTarget().set(target);
    }

    /**
     * Enables {@linkplain #getDryRun() dry-run} mode from the {@code --preview} command-line
     * option.
     *
     * @param dryRun {@code true} to preview the plan without executing migrations
     */
    @Option(option = "preview", description = "Show what would be executed without making changes")
    public void setDryRunOption(boolean dryRun) {
        getDryRun().set(dryRun);
    }

    /**
     * Task action that executes the forward migrations.
     *
     * <p>Loads the execution context, asks {@link UpService} what applying would do, prints the
     * execution graph, and — unless in dry-run mode — runs the migrations. Initializes the history
     * repository before execution.
     *
     * @throws GradleException if the target node is not found, if anything refuses the run (a task
     *     depending on a migration that is not defined, or one defining neither a rollback nor a
     *     reason there is none), or if any migration fails
     */
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

                    UpPlan plan = new UpService(context.graph(), historyRepo).plan(targetId);
                    UpBlocker blocker = plan.blocker();
                    if (blocker != null) {
                        throw new GradleException(
                                String.join(
                                        System.lineSeparator(), UpPlanFormatter.format(blocker)));
                    }

                    Set<NodeId> targetNodes = plan.targetNodes();

                    if (targetNodes.isEmpty()) {
                        getLogger()
                                .lifecycle(
                                        "No migrations to execute. All migrations are up to date.");
                        return;
                    }

                    ExecutionPlan executionPlan =
                            TopologicalSort.createExecutionPlanFor(context.graph(), targetNodes);
                    displayMigrationGraph(context, executionPlan, historyRepo, dryRun);

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

    /** Creates the task and marks it as never up to date, since it has side effects. */
    public MigrapheUpTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    /**
     * Creates the {@link Executor} for the UP direction, applying the project's parallelism
     * configuration ({@code maxParallelism} when {@code parallel} is enabled, otherwise 1).
     *
     * @param context the loaded execution context providing the graph
     * @param historyRepo the history repository tracking executed migrations
     * @param listener the listener that receives execution events
     * @return a configured executor for forward migrations
     */
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

        // Filter the plan's nodes in DFS order.
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
