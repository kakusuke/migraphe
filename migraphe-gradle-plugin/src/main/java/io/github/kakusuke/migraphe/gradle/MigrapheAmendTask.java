package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.execution.AmendService;
import io.github.kakusuke.migraphe.core.execution.AmendService.AmendEntry;
import io.github.kakusuke.migraphe.core.execution.AmendService.AmendPlan;
import io.github.kakusuke.migraphe.core.execution.UpContentState;
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
 * migration history — no database objects are touched — and its scope is exactly the nodes whose
 * recorded fingerprint is missing or differs from their task file.
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

                    HistoryRepository historyRepo = context.createHistoryRepository();
                    historyRepo.initialize();

                    AmendService service = new AmendService(context.graph(), historyRepo);
                    AmendPlan plan = service.plan();

                    if (plan.toRecord().isEmpty()) {
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
                        if (entry.from() == UpContentState.CHANGED) {
                            getLogger()
                                    .lifecycle(
                                            "             ⚠ edited after it was applied; what"
                                                    + " actually ran will no longer be recorded");
                        }
                    }
                    getLogger().lifecycle("");

                    int planned = plan.toRecord().size();
                    if (dryRun) {
                        getLogger()
                                .lifecycle(
                                        planned
                                                + fingerprints(planned)
                                                + " would be recorded. No changes made (dry run).");
                        return;
                    }

                    int written = service.apply(plan);
                    getLogger().lifecycle("Recorded {}{}.", written, fingerprints(written));

                    if (written < planned) {
                        throw new GradleException(
                                (planned - written)
                                        + " of "
                                        + planned
                                        + " could not be recorded: the history row was gone by the"
                                        + " time it was written.");
                    }
                });
    }

    /** The marker {@code status} shows for the state a node is being moved away from. */
    private static String fromMarker(UpContentState state) {
        return switch (state) {
            case UNKNOWN -> "[?]";
            case CHANGED -> "[!]";
            case NOT_APPLICABLE, UNCHANGED, UNREADABLE -> "[✓]";
        };
    }

    private static String fingerprints(int count) {
        return count == 1 ? " fingerprint" : " fingerprints";
    }
}
