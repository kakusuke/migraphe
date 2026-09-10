package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Gradle task that creates the migration history in the store {@code history.target} names.
 *
 * <p>Registered as {@code migrapheInit} by {@link MigrapheGradlePlugin}. It exists so that creating
 * the history is something an operator does rather than something that happens to whichever task
 * runs first: every task used to create it, which meant {@code migrapheStatus --check} in CI wrote
 * DDL to a database while reporting that nothing had been applied.
 *
 * <p>{@code migrapheUp} is the exception and creates one when it finds none, because applying the
 * first migration is the moment a project's history should come into being. Every other task
 * refuses and names this one — a task that only reports must not be told to change the database in
 * order to report.
 *
 * <p>Running it against a store that already has a history writes nothing and says so. It does not
 * alter one an older release created either; that is {@code migrapheUpgradeHistory}.
 */
@DisableCachingByDefault(
        because = "Migraphe tasks have side effects and their output cannot be cached")
public abstract class MigrapheInitTask extends AbstractMigrapheTask {

    /** Creates the task and marks it as never up to date, since it has side effects. */
    public MigrapheInitTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    /** Task action that creates the history unless it is already there. */
    @TaskAction
    public void init() {
        withExecutionContext(
                context -> {
                    HistoryRepository historyRepo = context.createHistoryRepository();

                    if (historyRepo.isInitialized()) {
                        getLogger().lifecycle("The migration history already exists.");
                        return;
                    }

                    historyRepo.initialize();
                    getLogger().lifecycle("Created the migration history.");
                });
    }
}
