package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.history.HistoryUpgrade;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.execution.HistoryRefusalFormatter;
import io.github.kakusuke.migraphe.core.execution.RepairVocabulary;
import io.github.kakusuke.migraphe.core.execution.UpgradeHistoryService;
import io.github.kakusuke.migraphe.core.execution.UpgradeHistoryService.UpgradeOutcome;
import io.github.kakusuke.migraphe.core.execution.UpgradeHistoryService.UpgradePlan;

/**
 * Brings the history to the shape this version writes.
 *
 * <p>This is the one command that changes a history an older release created. {@code initialize()}
 * creates and never alters, so a project that has just started needs nothing from here; a project
 * whose history predates this release runs this once, when its operator schedules it rather than
 * when someone happens to run {@code status}. That matters because a history can be shared with a
 * deployment still on the older version, which is still reading the columns an upgrade renames.
 *
 * <p>It is <strong>not</strong> {@code amend}. Amend records what the definitions say now because
 * an operator decided that is right, and writes every column they determine — which is why deleting
 * a {@code down:} and amending overwrites the recorded rollback with nothing. An upgrade completes
 * what an older version wrote incompletely and carries no such decision.
 *
 * <p>The name does not change between releases. Whatever a given version has to do to a history it
 * did not write, the release note and every refusal name this command.
 */
public class UpgradeHistoryCommand implements Command {

    private final ExecutionContext context;

    /**
     * Creates the upgrade command.
     *
     * @param context the loaded execution context
     */
    public UpgradeHistoryCommand(ExecutionContext context) {
        this.context = context;
    }

    @Override
    public int execute() {
        try {
            HistoryRepository historyRepo = context.createHistoryRepository();

            // A history that does not exist reports every upgrade as pending — each detection
            // query finds nothing — so upgrading it would alter a table that is not there.
            if (!historyRepo.isInitialized()) {
                HistoryRefusalFormatter.notInitializedLines(RepairVocabulary.CLI)
                        .forEach(System.err::println);
                return 1;
            }

            UpgradeHistoryService service = new UpgradeHistoryService(context.graph(), historyRepo);
            UpgradePlan plan = service.plan();

            if (plan.isUpToDate()) {
                System.out.println("The history is already up to date.");
                return 0;
            }

            System.out.println("Upgrading the history");
            System.out.println("=====================");
            System.out.println();
            for (HistoryUpgrade upgrade : plan.pending()) {
                System.out.println("  " + upgrade.description());
            }
            System.out.println();

            UpgradeOutcome outcome = service.apply(plan);

            System.out.println("Applied " + outcome.applied() + upgrades(outcome.applied()) + ".");
            return 0;

        } catch (Exception e) {
            System.err.println("Upgrade failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private static String upgrades(int count) {
        return count == 1 ? " upgrade" : " upgrades";
    }
}
