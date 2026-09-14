package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.history.HistoryUpgrade;
import io.github.kakusuke.migraphe.core.execution.HistoryRefusalFormatter;
import io.github.kakusuke.migraphe.core.execution.RepairVocabulary;
import io.github.kakusuke.migraphe.core.execution.UpgradeHistoryService;
import io.github.kakusuke.migraphe.core.execution.UpgradeHistoryService.UpgradeOutcome;
import io.github.kakusuke.migraphe.core.execution.UpgradeHistoryService.UpgradePlan;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Gradle task that brings the history to the shape this version writes.
 *
 * <p>Registered as {@code migrapheUpgradeHistory} by {@link MigrapheGradlePlugin}. It is the only
 * task that changes a history an older release created: every other task creates the table if it is
 * absent and otherwise leaves its shape alone, so a history shared with a deployment still on the
 * older version does not lose a column because someone ran {@code migrapheStatus} first.
 *
 * <p>It is <strong>not</strong> {@code migrapheAmend}. Amend records what the definitions say now
 * because an operator decided that is right, and writes every column they determine; an upgrade
 * completes what an older version wrote incompletely and touches nothing that already has a value.
 *
 * <p>There is no preview option and no confirmation. Every upgrade is guarded by its own detection,
 * so running this against a history that is already current does nothing and says so.
 */
@DisableCachingByDefault(
        because = "Migraphe tasks have side effects and their output cannot be cached")
public abstract class MigrapheUpgradeHistoryTask extends AbstractMigrapheTask {

    /** Creates the task and marks it as never up to date, since it has side effects. */
    public MigrapheUpgradeHistoryTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    /** Task action that applies every outstanding history upgrade, in order. */
    @TaskAction
    public void upgrade() {
        withExecutionContext(
                context -> {
                    HistoryRepository historyRepo = context.createHistoryRepository();

                    // A history that does not exist reports every upgrade as pending — each
                    // detection query finds nothing — so upgrading it would alter a missing table.
                    if (!historyRepo.isInitialized()) {
                        throw new GradleException(
                                String.join(
                                        System.lineSeparator(),
                                        HistoryRefusalFormatter.notInitializedLines(
                                                RepairVocabulary.GRADLE)));
                    }

                    UpgradeHistoryService service =
                            new UpgradeHistoryService(context.graph(), historyRepo);
                    UpgradePlan plan = service.plan();

                    if (plan.isUpToDate()) {
                        getLogger().lifecycle("The history is already up to date.");
                        return;
                    }

                    getLogger().lifecycle("Upgrading the history");
                    getLogger().lifecycle("=====================");
                    getLogger().lifecycle("");
                    for (HistoryUpgrade upgrade : plan.pending()) {
                        getLogger().lifecycle("  {}", upgrade.description());
                    }
                    getLogger().lifecycle("");

                    UpgradeOutcome outcome = service.apply(plan);

                    getLogger()
                            .lifecycle(
                                    "Applied {} upgrade{}.",
                                    outcome.applied(),
                                    outcome.applied() == 1 ? "" : "s");
                });
    }
}
