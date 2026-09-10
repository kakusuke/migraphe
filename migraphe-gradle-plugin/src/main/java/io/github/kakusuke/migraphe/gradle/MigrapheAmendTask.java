package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.execution.AmendBlocker;
import io.github.kakusuke.migraphe.core.execution.AmendNotice;
import io.github.kakusuke.migraphe.core.execution.AmendPlanFormatter;
import io.github.kakusuke.migraphe.core.execution.AmendService;
import io.github.kakusuke.migraphe.core.execution.AmendService.AmendEntry;
import io.github.kakusuke.migraphe.core.execution.AmendService.AmendOutcome;
import io.github.kakusuke.migraphe.core.execution.AmendService.AmendPlan;
import io.github.kakusuke.migraphe.core.execution.UpContentState;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

/**
 * Gradle task that records the current definitions as what was applied.
 *
 * <p>Registered as {@code migrapheAmend} by {@link MigrapheGradlePlugin}. It writes only to the
 * migration history — no database objects are touched. {@code --migration} names the one it acts
 * on, and is never implied: the row appended is built from the definition and states what it says
 * for the whole migration, whatever the history reported first — see {@link
 * io.github.kakusuke.migraphe.core.execution.AmendNotice}. Completing rows an older release wrote
 * incompletely is not that claim, and is {@code migrapheUpgradeHistory}'s job.
 *
 * <p>An id nothing defines and the history does not hold fails the build rather than reporting
 * nothing to do, since an empty plan is also what a migration that already agrees with its record
 * produces. One the history holds and the definitions no longer declare is not refused: it is
 * withdrawn — a row saying it is no longer applied.
 *
 * <p>There is no confirmation prompt, matching the other tasks: Gradle tasks read no stdin, and
 * {@code migrapheDown --all} likewise destroys database objects unprompted. Use {@code --preview}
 * first.
 */
@DisableCachingByDefault(
        because = "Migraphe tasks have side effects and their output cannot be cached")
public abstract class MigrapheAmendTask extends AbstractMigrapheTask {

    /**
     * Whether to display the plan without recording anything.
     *
     * @return the dry-run property
     */
    @Input
    @Optional
    public abstract Property<Boolean> getDryRun();

    /**
     * The one migration to amend.
     *
     * @return the migration property
     */
    @Input
    @Optional
    public abstract Property<String> getMigration();

    /**
     * Names the migration to amend from the {@code --migration} command line option.
     *
     * @param migration the migration's node id
     */
    @Option(option = "migration", description = "Migration to record the current definition of")
    public void setMigrationOption(String migration) {
        getMigration().set(migration);
    }

    /**
     * Enables dry-run mode from the {@code --preview} command line option.
     *
     * @param dryRun {@code true} to display the plan without recording
     */
    @Option(option = "preview", description = "Show plan without recording")
    public void setPreviewOption(boolean dryRun) {
        getDryRun().set(dryRun);
    }

    /** Creates the task and marks it as never up to date, since it has side effects. */
    public MigrapheAmendTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    /** Task action that records the planned fingerprints, or displays them in dry-run mode. */
    @TaskAction
    public void amend() {
        withExecutionContext(
                context -> {
                    boolean dryRun = getDryRun().getOrElse(false);
                    String migration = getMigration().getOrNull();

                    // Amending states what the definitions say for the whole migration and
                    // replaces what the history reported, so it is never implied. Completing rows
                    // an older release left incomplete is not that claim; migrapheUpgradeHistory
                    // is.
                    if (migration == null) {
                        throw new GradleException(
                                "--migration must be specified.\n"
                                        + "Usage:\n"
                                        + "  ./gradlew migrapheAmend --migration=<nodeId>\n"
                                        + "To complete rows a previous version left incomplete,"
                                        + " run './gradlew migrapheUpgradeHistory'.");
                    }

                    HistoryRepository historyRepo = context.createHistoryRepository();
                    historyRepo.initialize();

                    AmendService service = new AmendService(context.graph(), historyRepo);
                    AmendPlan plan = service.plan(NodeId.of(migration));

                    AmendBlocker blocker = plan.blocker();
                    if (blocker != null) {
                        throw new GradleException(
                                String.join(
                                        System.lineSeparator(),
                                        AmendPlanFormatter.format(blocker)));
                    }

                    if (plan.toRecord().isEmpty() && plan.toWithdraw().isEmpty()) {
                        getLogger().lifecycle("Nothing to amend.");
                        return;
                    }

                    getLogger()
                            .lifecycle(
                                    (dryRun ? "[DRY RUN] " : "")
                                            + "Amend plan (history only — no database changes):");
                    getLogger().lifecycle("");
                    for (AmendEntry entry : plan.toRecord()) {
                        getLogger()
                                .lifecycle(
                                        "  {} → [✓]  {} - {}",
                                        fromMarker(entry.from()),
                                        entry.node().id().value(),
                                        entry.node().name());
                    }
                    for (NodeId withdrawn : plan.toWithdraw()) {
                        getLogger().lifecycle("  [✓] → [ ]  {}", withdrawn.value());
                    }
                    getLogger().lifecycle("");
                    if (!plan.toRecord().isEmpty()) {
                        for (String line : AmendNotice.LINES) {
                            getLogger().lifecycle(line);
                        }
                    }
                    if (!plan.toWithdraw().isEmpty()) {
                        for (String line : AmendNotice.WITHDRAWAL_LINES) {
                            getLogger().lifecycle(line);
                        }
                    }
                    getLogger().lifecycle("");

                    if (dryRun) {
                        getLogger()
                                .lifecycle(
                                        String.join(" ", plannedSizes(plan))
                                                + " No changes made (dry run).");
                        return;
                    }

                    AmendOutcome outcome = service.apply(plan);
                    if (outcome.recorded() > 0) {
                        getLogger()
                                .lifecycle(
                                        "Recorded {}{}.",
                                        outcome.recorded(),
                                        fingerprints(outcome.recorded()));
                    }
                    if (outcome.withdrawn() > 0) {
                        getLogger()
                                .lifecycle(
                                        "Withdrew {}{}.",
                                        outcome.withdrawn(),
                                        migrations(outcome.withdrawn()));
                    }
                });
    }

    /** What the plan would do, stated per kind: a withdrawal appends no fingerprint. */
    private static List<String> plannedSizes(AmendPlan plan) {
        List<String> sizes = new ArrayList<>();
        if (!plan.toRecord().isEmpty()) {
            int planned = plan.toRecord().size();
            sizes.add(planned + fingerprints(planned) + " would be recorded.");
        }
        if (!plan.toWithdraw().isEmpty()) {
            int planned = plan.toWithdraw().size();
            sizes.add(planned + migrations(planned) + " would be withdrawn.");
        }
        return sizes;
    }

    /**
     * The marker {@code status} shows for the state a migration is being moved away from.
     *
     * <p>{@code NOT_APPLICABLE} reaches a plan only for a migration the history reports as not
     * applied — never recorded, rolled back, or one whose every attempt failed. An <em>applied</em>
     * migration cannot report it at all: a plugin that folds no token reports as unreadable. So it
     * renders as {@code [ ]}, the same marker {@code status} gives it. {@code UNCHANGED} and {@code
     * UNREADABLE} cannot reach a plan; they are listed so that adding a state to {@link
     * UpContentState} stops this compiling.
     */
    private static String fromMarker(UpContentState state) {
        return switch (state) {
            case NOT_APPLICABLE -> "[ ]";
            case UNKNOWN -> "[?]";
            case CHANGED -> "[!]";
            case UNCHANGED, UNREADABLE -> "[✓]";
        };
    }

    private static String fingerprints(int count) {
        return count == 1 ? " fingerprint" : " fingerprints";
    }

    private static String migrations(int count) {
        return count == 1 ? " migration" : " migrations";
    }
}
