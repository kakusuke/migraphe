package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.execution.AmendService;
import io.github.kakusuke.migraphe.core.execution.AmendService.AmendEntry;
import io.github.kakusuke.migraphe.core.execution.AmendService.AmendPlan;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Scanner;

/**
 * The {@code amend} command, which records the current definitions as what was applied.
 *
 * <p>Amend resolves drift in the history's favour: for every node whose recorded fingerprint is
 * missing or differs from its task file, it writes the current fingerprint. <strong>No database
 * objects are touched.</strong> Its counterpart, which resolves drift the other way by rolling back
 * and re-applying, is a separate command.
 *
 * <p>There is no node argument and no {@code --all}: the scope is exactly the set of nodes that
 * have drifted, which is what every use case for this asks for.
 */
public class AmendCommand implements Command {

    private final ExecutionContext context;
    private final boolean skipConfirmation;
    private final boolean dryRun;
    private final InputStream inputStream;

    /**
     * Creates the command, reading confirmation from {@link System#in}.
     *
     * @param context the loaded execution context (graph, config, history)
     * @param skipConfirmation {@code true} to skip the interactive confirmation prompt
     * @param dryRun {@code true} to display the plan without writing anything
     */
    public AmendCommand(ExecutionContext context, boolean skipConfirmation, boolean dryRun) {
        this(context, skipConfirmation, dryRun, System.in);
    }

    /**
     * Creates the command with an explicit confirmation source.
     *
     * @param context the loaded execution context (graph, config, history)
     * @param skipConfirmation {@code true} to skip the interactive confirmation prompt
     * @param dryRun {@code true} to display the plan without writing anything
     * @param inputStream where the confirmation answer is read from
     */
    public AmendCommand(
            ExecutionContext context,
            boolean skipConfirmation,
            boolean dryRun,
            InputStream inputStream) {
        this.context = context;
        this.skipConfirmation = skipConfirmation;
        this.dryRun = dryRun;
        this.inputStream = inputStream;
    }

    @Override
    public int execute() {
        try {
            HistoryRepository historyRepo = context.createHistoryRepository();
            historyRepo.initialize();

            AmendService service = new AmendService(context.graph(), historyRepo);
            AmendPlan plan = service.plan();

            if (plan.toRecord().isEmpty()) {
                System.out.println("Nothing to amend.");
                return 0;
            }

            displayPlan(plan);

            if (dryRun) {
                System.out.println();
                System.out.println("No changes made (dry run).");
                return 0;
            }

            if (!skipConfirmation && !confirmAmend(plan)) {
                System.out.println("Amend cancelled.");
                return 0;
            }

            int planned = plan.toRecord().size();
            int written = service.apply(plan);

            System.out.println();
            System.out.println("Recorded " + written + fingerprints(written) + ".");

            if (written < planned) {
                System.out.println(
                        (planned - written)
                                + " could not be recorded: the history row was gone by the time it"
                                + " was written.");
                return 1;
            }
            return 0;

        } catch (Exception e) {
            System.err.println("Amend failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    /** Lists the nodes whose fingerprint would be recorded. */
    private void displayPlan(AmendPlan plan) {
        String prefix = dryRun ? "[DRY RUN] " : "";

        System.out.println();
        System.out.println(prefix + "Amend plan (history only — no database changes):");
        System.out.println();

        for (AmendEntry entry : plan.toRecord()) {
            System.out.println("  " + entry.node().id().value() + " - " + entry.node().name());
        }

        int planned = plan.toRecord().size();
        System.out.println();
        System.out.println(
                planned
                        + fingerprints(planned)
                        + (dryRun ? " would be" : " will be")
                        + " recorded.");
    }

    /** Displays the confirmation prompt and returns whether the user approved the amend. */
    private boolean confirmAmend(AmendPlan plan) {
        int planned = plan.toRecord().size();
        System.out.println();
        System.out.print("Record " + planned + fingerprints(planned) + "? [y/N]: ");
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
            String input = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
            return "y".equals(input) || "yes".equals(input);
        }
    }

    private static String fingerprints(int count) {
        return count == 1 ? " fingerprint" : " fingerprints";
    }
}
