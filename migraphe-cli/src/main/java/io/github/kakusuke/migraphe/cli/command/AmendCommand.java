package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.execution.AmendBlocker;
import io.github.kakusuke.migraphe.core.execution.AmendNotice;
import io.github.kakusuke.migraphe.core.execution.AmendPlanFormatter;
import io.github.kakusuke.migraphe.core.execution.AmendService;
import io.github.kakusuke.migraphe.core.execution.AmendService.AmendEntry;
import io.github.kakusuke.migraphe.core.execution.AmendService.AmendOutcome;
import io.github.kakusuke.migraphe.core.execution.AmendService.AmendPlan;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.execution.UpContentState;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
 * <p>An id the command cannot act on is refused <em>before</em> the nothing-to-do branch, because
 * an empty plan is sometimes the honest answer — a migration that is defined and already agrees has
 * nothing to amend, and saying so is a success. Behind that branch the refusal would never be
 * reached, and a typo would report success again.
 *
 * <p>Two scopes, differing in <em>which</em> migrations they take. Naming a migration takes one
 * whose fingerprint differs, one the history has never recorded — the claim that brings an existing
 * database under migraphe — or an orphan, which is claimed <em>not</em> applied because the
 * definitions no longer describe it; so the named form is never implied. The bulk form takes every
 * migration whose <em>row</em> carries no fingerprint, which is the upgrade path, and never claims
 * one with no row at all. The plan states unconditionally what the history will report — see {@link
 * io.github.kakusuke.migraphe.core.execution.AmendNotice}.
 */
public class AmendCommand implements Command {

    private final ExecutionContext context;
    private final NodeId nodeId;
    private final boolean skipConfirmation;
    private final boolean dryRun;
    private final InputStream inputStream;

    /**
     * Creates the command, reading confirmation from {@link System#in}.
     *
     * @param context the loaded execution context (graph, config, history)
     * @param nodeId the one migration to amend
     * @param skipConfirmation {@code true} to skip the interactive confirmation prompt
     * @param dryRun {@code true} to display the plan without writing anything
     */
    public AmendCommand(
            ExecutionContext context, NodeId nodeId, boolean skipConfirmation, boolean dryRun) {
        this(context, nodeId, skipConfirmation, dryRun, System.in);
    }

    /**
     * Creates the command with an explicit confirmation source.
     *
     * @param context the loaded execution context (graph, config, history)
     * @param nodeId the one migration to amend
     * @param skipConfirmation {@code true} to skip the interactive confirmation prompt
     * @param dryRun {@code true} to display the plan without writing anything
     * @param inputStream where the confirmation answer is read from
     */
    public AmendCommand(
            ExecutionContext context,
            NodeId nodeId,
            boolean skipConfirmation,
            boolean dryRun,
            InputStream inputStream) {
        this.context = context;
        this.nodeId = nodeId;
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
            AmendPlan plan = service.plan(nodeId);

            AmendBlocker blocker = plan.blocker();
            if (blocker != null) {
                AmendPlanFormatter.format(blocker).forEach(System.err::println);
                return 1;
            }

            if (plan.toRecord().isEmpty() && plan.toWithdraw().isEmpty()) {
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

            AmendOutcome outcome = service.apply(plan);

            System.out.println();
            if (outcome.recorded() > 0) {
                System.out.println(
                        "Recorded " + outcome.recorded() + fingerprints(outcome.recorded()) + ".");
            }
            if (outcome.withdrawn() > 0) {
                System.out.println(
                        "Withdrew " + outcome.withdrawn() + migrations(outcome.withdrawn()) + ".");
            }
            if (outcome.recorded() == 0 && outcome.withdrawn() == 0) {
                System.out.println("Nothing was written.");
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
            System.out.println(
                    "  "
                            + fromMarker(entry.from())
                            + " → [✓]  "
                            + entry.node().id().value()
                            + " - "
                            + entry.node().name());
        }

        for (NodeId withdrawn : plan.toWithdraw()) {
            System.out.println("  [✓] → [ ]  " + withdrawn.value());
        }

        System.out.println();
        if (!plan.toRecord().isEmpty()) {
            for (String line : AmendNotice.LINES) {
                System.out.println(line);
            }
        }
        if (!plan.toWithdraw().isEmpty()) {
            for (String line : AmendNotice.WITHDRAWAL_LINES) {
                System.out.println(line);
            }
        }

        String tense = dryRun ? " would be " : " will be ";
        System.out.println();
        if (!plan.toRecord().isEmpty()) {
            int planned = plan.toRecord().size();
            System.out.println(planned + fingerprints(planned) + tense + "recorded.");
        }
        if (!plan.toWithdraw().isEmpty()) {
            int planned = plan.toWithdraw().size();
            System.out.println(planned + migrations(planned) + tense + "withdrawn.");
        }
    }

    /** Displays the confirmation prompt and returns whether the user approved the amend. */
    private boolean confirmAmend(AmendPlan plan) {
        List<String> asks = new ArrayList<>();
        if (!plan.toRecord().isEmpty()) {
            int planned = plan.toRecord().size();
            asks.add("Record " + planned + fingerprints(planned));
        }
        if (!plan.toWithdraw().isEmpty()) {
            int planned = plan.toWithdraw().size();
            asks.add("Withdraw " + planned + migrations(planned));
        }
        System.out.println();
        System.out.print(String.join(" and ", asks) + "? [y/N]: ");
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
            String input = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
            return "y".equals(input) || "yes".equals(input);
        }
    }

    /**
     * The marker {@code status} shows for the state a node is being moved away from.
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
