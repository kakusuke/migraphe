package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.core.execution.DagExecutor;
import io.github.kakusuke.migraphe.core.execution.DownBlocker;
import io.github.kakusuke.migraphe.core.execution.DownPlanFormatter;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.execution.ExecutionResult;
import io.github.kakusuke.migraphe.core.execution.HistoryReadiness;
import io.github.kakusuke.migraphe.core.execution.HistoryRefusalFormatter;
import io.github.kakusuke.migraphe.core.execution.RebuildService;
import io.github.kakusuke.migraphe.core.execution.RebuildService.RebuildPlan;
import io.github.kakusuke.migraphe.core.execution.RepairVocabulary;
import io.github.kakusuke.migraphe.core.execution.UpBlocker;
import io.github.kakusuke.migraphe.core.execution.UpPlanFormatter;
import io.github.kakusuke.migraphe.core.execution.UpService;
import io.github.kakusuke.migraphe.core.execution.UpService.UpPlan;
import java.util.Comparator;
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
 * Gradle task that rolls back what no longer matches the definitions and applies everything again.
 *
 * <p>Registered as {@code migrapheRebuild} by {@link MigrapheGradlePlugin}. The sets it acts on
 * come from {@link RebuildService}, so this task and the CLI command decide the same thing.
 *
 * <p>There is no confirmation prompt, matching the other tasks: Gradle tasks read no stdin, and
 * {@code migrapheDown --all} likewise destroys database objects unprompted. Use {@code --preview}
 * first.
 */
@DisableCachingByDefault(
        because = "Migraphe tasks have side effects and their output cannot be cached")
public abstract class MigrapheRebuildTask extends AbstractMigrapheTask {

    /**
     * Whether to display the plan without changing anything.
     *
     * @return the dry-run property
     */
    @Input
    @Optional
    public abstract Property<Boolean> getDryRun();

    /**
     * Enables dry-run mode from the {@code --preview} command line option.
     *
     * @param dryRun {@code true} to display the plan without rebuilding
     */
    @Option(option = "preview", description = "Show plan without rebuilding")
    public void setPreviewOption(boolean dryRun) {
        getDryRun().set(dryRun);
    }

    /** Creates the task and marks it as never up to date, since it has side effects. */
    public MigrapheRebuildTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    /** Task action that rebuilds the difference set, or displays it in dry-run mode. */
    @TaskAction
    public void rebuild() {
        withExecutionContext(
                context -> {
                    boolean dryRun = getDryRun().getOrElse(false);

                    HistoryRepository historyRepo = context.createHistoryRepository();
                    List<String> notReady =
                            HistoryReadiness.refusal(historyRepo, RepairVocabulary.GRADLE);
                    if (!notReady.isEmpty()) {
                        throw new GradleException(String.join(System.lineSeparator(), notReady));
                    }

                    RebuildPlan plan =
                            new RebuildService(context.graph(), historyRepo)
                                    .plan(context.targets().values());

                    // A row naming a target the project no longer configures stops the run
                    // before anything else is weighed: there is no connection to take that
                    // migration out through, and the rest of the rollback would remove what it
                    // still stands on. `down` refuses the same state in the same words.
                    if (!plan.unresolvableRows().isEmpty()) {
                        throw new GradleException(
                                String.join(
                                        System.lineSeparator(),
                                        DownPlanFormatter.format(
                                                new DownBlocker.UnresolvableTargets(
                                                        plan.unresolvableRows()),
                                                RepairVocabulary.GRADLE)));
                    }

                    // A comparison that threw is not evidence of agreement, and unlike a row
                    // that predates a column it is not one repair away: the plugin could not
                    // answer, so nothing an operator runs will make it answer.
                    if (!plan.unreadable().isEmpty()) {
                        throw new GradleException(
                                String.join(
                                        System.lineSeparator(),
                                        HistoryRefusalFormatter.unreadableLines(
                                                plan.unreadable(), RepairVocabulary.GRADLE)));
                    }

                    // A migration that cannot come down is reported before the plan: there is no
                    // version of this run that succeeds.
                    if (!plan.frozen().isEmpty()) {
                        throw new GradleException(
                                String.join(
                                        System.lineSeparator(),
                                        DownPlanFormatter.rebuildFrozenLines(
                                                plan.frozen().size(), plan.irreversible())));
                    }

                    // "Not known to differ" is not "known to agree".
                    if (!plan.incomplete().isEmpty()) {
                        throw new GradleException(
                                String.join(
                                        System.lineSeparator(),
                                        HistoryRefusalFormatter.incompleteLines(
                                                plan.incomplete(), RepairVocabulary.GRADLE)));
                    }

                    if (plan.toRebuild().isEmpty() && plan.toRollBack().isEmpty()) {
                        getLogger().lifecycle("Nothing to rebuild.");
                        return;
                    }

                    String prefix = dryRun ? "[DRY RUN] " : "";
                    getLogger().lifecycle("");
                    if (!plan.toRollBack().isEmpty()) {
                        // Every migration that comes down, not only the ones that differ — see
                        // RebuildCommand for why the two must not be reported as one.
                        getLogger().lifecycle("{}Migrations to roll back:", prefix);
                        getLogger().lifecycle("");
                        for (NodeId node : sorted(plan.toRollBack())) {
                            getLogger()
                                    .lifecycle(
                                            "  {} {}",
                                            plan.toRebuild().contains(node) ? "[!]" : "[✓]",
                                            node.value());
                        }
                        getLogger().lifecycle("");
                    }
                    List<NodeId> notComingBack = permanentlyRemoved(plan, context);
                    if (!notComingBack.isEmpty()) {
                        getLogger()
                                .lifecycle(
                                        "{}Permanently removed — no task file declares these any"
                                                + " more, so they come out and do not go back:",
                                        prefix);
                        getLogger().lifecycle("");
                        for (NodeId node : notComingBack) {
                            getLogger().lifecycle("  [-] {}", node.value());
                        }
                        getLogger().lifecycle("");
                    }
                    getLogger()
                            .lifecycle(
                                    "Rolling back {} migration(s) — [!] differs from its"
                                            + " definition, [✓] stands on one that does — then"
                                            + " applying the whole graph.",
                                    plan.toRollBack().size());

                    if (dryRun) {
                        getLogger().lifecycle("");
                        getLogger().lifecycle("No changes made (dry run).");
                        return;
                    }

                    GradleExecutionListener listener = new GradleExecutionListener(getLogger());
                    ExecutionResult down =
                            // Sequential because that is how `down` rolls back, not because a
                            // rebuild decided so. When `down` reads the configuration this
                            // follows it.
                            new DagExecutor(
                                            plan.graph(),
                                            historyRepo,
                                            listener,
                                            ExecutionDirection.DOWN,
                                            1)
                                    .execute(plan.toRollBack());
                    if (!down.success()) {
                        throw new GradleException(
                                "Rebuild stopped: the rollback did not complete.");
                    }

                    // Asking UpService rather than replaying the rollback set keeps a node that
                    // failed to come down from being applied over.
                    UpPlan upPlan =
                            new UpService(context.graph(), historyRepo)
                                    .planWhileRepairingDrift(null);
                    UpBlocker upBlocker = upPlan.blocker();
                    if (upBlocker != null) {
                        throw new GradleException(
                                String.join(
                                        System.lineSeparator(),
                                        UpPlanFormatter.format(
                                                upBlocker, RepairVocabulary.GRADLE)));
                    }
                    ExecutionResult up =
                            new DagExecutor(
                                            context.graph(),
                                            historyRepo,
                                            listener,
                                            ExecutionDirection.UP,
                                            context.maxParallelism())
                                    .execute(upPlan.selectedNodes());
                    if (!up.success()) {
                        throw new GradleException("Rebuild stopped: re-applying did not complete.");
                    }
                });
    }

    /**
     * What this run takes out and does not put back, ordered by id.
     *
     * <p>Everything the rollback phase removes is re-applied afterwards <em>except</em> what the
     * definitions no longer declare: there is nothing left to put back. A rebuild is therefore
     * partly a permanent removal, and an operator has to be told which part before agreeing to it.
     */
    private static List<NodeId> permanentlyRemoved(RebuildPlan plan, ExecutionContext context) {
        return plan.toRollBack().stream()
                .filter(id -> context.graph().getNode(id).isEmpty())
                .sorted(java.util.Comparator.comparing(NodeId::value))
                .toList();
    }

    /** Node ids in a stable order, so the plan reads the same twice. */
    private static List<NodeId> sorted(Set<NodeId> nodes) {
        return nodes.stream().sorted(Comparator.comparing(NodeId::value)).toList();
    }
}
