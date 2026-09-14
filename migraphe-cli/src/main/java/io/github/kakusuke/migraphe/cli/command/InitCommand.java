package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;

/**
 * Creates the migration history in the store {@code history.target} names.
 *
 * <p>It exists so that creating the history is something an operator does, rather than something
 * that happens to whoever runs a command first. Every command used to create it, which meant {@code
 * status --check} in CI wrote DDL to a database while reporting that nothing had been applied. Only
 * this command and {@code up} create one now; everything else refuses and names this one.
 *
 * <p>{@code up} is the exception because applying the first migration is the moment a project's
 * history should come into being — a fresh project still needs no ceremony before its first {@code
 * up}. That is a convenience, not a remedy: a refusal from a command that only reports names this
 * command and never offers {@code up}, because answering "I cannot report" with "then change the
 * database" is not an answer.
 *
 * <p>Running it against a store that already has a history writes nothing and says so. It does not
 * alter one an older release created either — that is {@code upgrade-history}.
 */
public class InitCommand implements Command {

    private final ExecutionContext context;

    /**
     * Creates the init command.
     *
     * @param context the loaded execution context
     */
    public InitCommand(ExecutionContext context) {
        this.context = context;
    }

    @Override
    public int execute() {
        try {
            HistoryRepository historyRepo = context.createHistoryRepository();

            if (historyRepo.isInitialized()) {
                System.out.println("The migration history already exists.");
                return 0;
            }

            historyRepo.initialize();
            System.out.println("Created the migration history.");
            return 0;

        } catch (Exception e) {
            System.err.println("Init failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
