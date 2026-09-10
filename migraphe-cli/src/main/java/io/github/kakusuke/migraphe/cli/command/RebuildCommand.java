package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.cli.listener.ConsoleExecutionListener;
import io.github.kakusuke.migraphe.core.execution.DagExecutor;
import io.github.kakusuke.migraphe.core.execution.DownBlocker;
import io.github.kakusuke.migraphe.core.execution.DownPlanFormatter;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.execution.ExecutionResult;
import io.github.kakusuke.migraphe.core.execution.HistoryRefusalFormatter;
import io.github.kakusuke.migraphe.core.execution.RebuildService;
import io.github.kakusuke.migraphe.core.execution.RebuildService.RebuildPlan;
import io.github.kakusuke.migraphe.core.execution.RepairVocabulary;
import io.github.kakusuke.migraphe.core.execution.UpBlocker;
import io.github.kakusuke.migraphe.core.execution.UpPlanFormatter;
import io.github.kakusuke.migraphe.core.execution.UpService;
import io.github.kakusuke.migraphe.core.execution.UpService.UpPlan;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;

/**
 * Rolls back what no longer matches the definitions and applies everything again.
 *
 * <p>Stated as a DAG difference: the migrations whose recorded content differs from the definitions
 * come out, together with whatever was built on top of them, and then the whole graph is applied.
 * {@link RebuildService} decides the sets; this command renders them, asks, and runs.
 *
 * <p>It takes <strong>no migration argument</strong>. {@code rebuild <id>} would be {@code down
 * <id>} followed by {@code up}, which already exists; the operation that is new is rebuilding the
 * difference set, and naming one node would be a way to leave the job half done.
 */
public class RebuildCommand implements Command {

    private final ExecutionContext context;
    private final boolean skipConfirmation;
    private final boolean dryRun;
    private final InputStream inputStream;

    /**
     * Creates the rebuild command.
     *
     * @param context the loaded execution context
     * @param skipConfirmation {@code true} to proceed without prompting
     * @param dryRun {@code true} to display the plan and change nothing
     */
    public RebuildCommand(ExecutionContext context, boolean skipConfirmation, boolean dryRun) {
        this(context, skipConfirmation, dryRun, System.in);
    }

    /**
     * Creates the rebuild command, reading the confirmation from a supplied stream.
     *
     * @param context the loaded execution context
     * @param skipConfirmation {@code true} to proceed without prompting
     * @param dryRun {@code true} to display the plan and change nothing
     * @param inputStream where the confirmation is read from
     */
    public RebuildCommand(
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

            RebuildPlan plan =
                    new RebuildService(context.graph(), historyRepo)
                            .plan(context.targets().values());

            // A row naming a target the project no longer configures stops the run before anything
            // else is weighed: there is no connection to take that migration out through, and the
            // rest of the rollback would remove what it still stands on. `down` refuses the same
            // state in the same words.
            if (!plan.unresolvableRows().isEmpty()) {
                DownPlanFormatter.format(
                                new DownBlocker.UnresolvableTargets(plan.unresolvableRows()),
                                RepairVocabulary.CLI)
                        .forEach(System.err::println);
                return 1;
            }

            // A comparison that threw is not evidence of agreement, and unlike a row that predates
            // a column it is not one repair away: the plugin could not answer, so nothing an
            // operator runs will make it answer.
            if (!plan.unreadable().isEmpty()) {
                HistoryRefusalFormatter.unreadableLines(plan.unreadable(), RepairVocabulary.CLI)
                        .forEach(System.err::println);
                return 1;
            }

            // A migration that cannot come down is the case nothing can do anything about, so it is
            // reported before the plan rather than after: there is no version of this run that
            // succeeds.
            if (!plan.frozen().isEmpty()) {
                DownPlanFormatter.rebuildFrozenLines(plan.frozen().size(), plan.irreversible())
                        .forEach(System.err::println);
                return 1;
            }

            // "Not known to differ" is not "known to agree". Rebuilding around a row whose
            // comparison cannot be made would be acting on the former as if it were the latter.
            if (!plan.incomplete().isEmpty()) {
                HistoryRefusalFormatter.incompleteLines(plan.incomplete(), RepairVocabulary.CLI)
                        .forEach(System.err::println);
                return 1;
            }

            // Asked before anything comes out of the database, and before the dry run returns.
            // A rebuild that cannot put back what it takes out has no version that succeeds, and
            // reporting that only after the rollback would leave the database torn down; a preview
            // that exits zero on a plan the real run would fail is not a rehearsal.
            UpBlocker upBlocker =
                    new UpService(context.graph(), historyRepo)
                            .planWhileRepairingDrift(null)
                            .blocker();
            if (upBlocker != null) {
                UpPlanFormatter.format(upBlocker, RepairVocabulary.CLI)
                        .forEach(System.err::println);
                return 1;
            }

            if (plan.toRebuild().isEmpty() && plan.toRollBack().isEmpty()) {
                System.out.println("Nothing to rebuild.");
                return 0;
            }

            String prefix = dryRun ? "[DRY RUN] " : "";
            System.out.println();
            if (!plan.toRollBack().isEmpty()) {
                // Every migration that comes down, not only the ones that differ. Listing the
                // difference set under a count that included its dependents said two numbers about
                // one plan, and left the operator unable to see which migrations the more
                // destructive of the two commands would take out.
                System.out.println(prefix + "Migrations to roll back:");
                System.out.println();
                for (NodeId node : sorted(plan.toRollBack())) {
                    String marker = plan.toRebuild().contains(node) ? "  [!] " : "  [✓] ";
                    System.out.println(marker + node.value());
                }
                System.out.println();
            }
            List<NodeId> notComingBack = permanentlyRemoved(plan, context);
            if (!notComingBack.isEmpty()) {
                System.out.println(
                        prefix
                                + "Permanently removed — no task file declares these any more, so"
                                + " they come out and do not go back:");
                System.out.println();
                for (NodeId node : notComingBack) {
                    System.out.println("  [-] " + node.value());
                }
                System.out.println();
            }
            System.out.println(
                    "Rolling back "
                            + plan.toRollBack().size()
                            + " migration(s) — [!] differs from its definition, [✓] stands on one"
                            + " that does — then applying the whole graph.");

            if (dryRun) {
                System.out.println();
                System.out.println("No changes made (dry run).");
                return 0;
            }

            if (!skipConfirmation && !confirmRebuild()) {
                System.out.println("Cancelled.");
                return 0;
            }

            ConsoleExecutionListener listener = new ConsoleExecutionListener(true);
            ExecutionResult down =
                    // Sequential because that is how `down` rolls back, not because a rebuild
                    // decided so. When `down` reads the configuration this follows it.
                    new DagExecutor(plan.graph(), historyRepo, listener, ExecutionDirection.DOWN, 1)
                            .execute(plan.toRollBack());
            if (!down.success()) {
                System.err.println("Rebuild stopped: the rollback did not complete.");
                return 1;
            }

            // Everything not applied, which after the rollback is the difference set and whatever
            // came down with it. Asking UpService rather than passing the rollback set keeps a node
            // that failed to come down out of the re-apply.
            //
            // Its refusal is read rather than assumed absent. The check before the rollback used
            // to be enough because every blocker was a property of the graph, and one of them now
            // reads the history — which the rollback just wrote to. No input is known to reach it,
            // and that is exactly why it must not be ignored: a blocker here empties the selected
            // set, so ignoring it would re-apply nothing and report success on a database that had
            // just been torn down. The sibling Gradle task has always read it.
            UpPlan upPlan =
                    new UpService(context.graph(), historyRepo).planWhileRepairingDrift(null);
            UpBlocker afterRollback = upPlan.blocker();
            if (afterRollback != null) {
                UpPlanFormatter.format(afterRollback, RepairVocabulary.CLI)
                        .forEach(System.err::println);
                System.err.println("Rebuild stopped: nothing was re-applied.");
                return 1;
            }
            ExecutionResult up =
                    new DagExecutor(
                                    context.graph(),
                                    historyRepo,
                                    listener,
                                    ExecutionDirection.UP,
                                    context.maxParallelism())
                            .execute(upPlan.selectedNodes());
            return up.success() ? 0 : 1;

        } catch (Exception e) {
            System.err.println("Failed to rebuild: " + e.getMessage());
            return 1;
        }
    }

    /** Displays the confirmation prompt and returns whether the operator approved. */
    private boolean confirmRebuild() {
        System.out.println();
        System.out.print("Proceed with rebuild? [y/N]: ");
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
            String input = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
            return "y".equals(input) || "yes".equals(input);
        }
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
        return nodes.stream().sorted(java.util.Comparator.comparing(NodeId::value)).toList();
    }
}
