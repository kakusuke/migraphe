package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.cli.listener.ConsoleExecutionListener;
import io.github.kakusuke.migraphe.cli.util.AnsiColor;
import io.github.kakusuke.migraphe.core.execution.DagExecutor;
import io.github.kakusuke.migraphe.core.execution.DownBlocker;
import io.github.kakusuke.migraphe.core.execution.DownPlanFormatter;
import io.github.kakusuke.migraphe.core.execution.DownService;
import io.github.kakusuke.migraphe.core.execution.DownService.DownPlan;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.execution.ExecutionResult;
import io.github.kakusuke.migraphe.core.graph.ExecutionPlan;
import io.github.kakusuke.migraphe.core.graph.TopologicalSort;
import io.github.kakusuke.migraphe.core.graph.layout.ExecutionGraphView;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The {@code down} command, which rolls back (DOWN) previously executed migrations.
 *
 * <p>Determines the rollback set either from a target version (all executed nodes that transitively
 * depend on it) or, when {@code --all} is given, every executed node. The plan is rendered as a
 * reversed graph, confirmation is requested unless skipped, and the rollback is run via a {@link
 * DagExecutor} in the {@link ExecutionDirection#DOWN} direction. DOWN execution is always
 * sequential (parallelism of {@code 1}). In dry-run mode the plan is displayed but nothing is
 * executed.
 */
public class DownCommand implements Command {

    private final ExecutionContext context;
    private final @Nullable NodeId targetVersion;
    private final boolean allMigrations;
    private final boolean skipConfirmation;
    private final boolean dryRun;
    private final InputStream inputStream;
    private final boolean colorEnabled;

    /**
     * Creates a command with the given options, reading confirmation from {@link System#in} and
     * auto-detecting color support.
     *
     * @param context the loaded execution context (graph, config, history)
     * @param targetVersion the node version to roll back (together with everything depending on
     *     it), or {@code null} when {@code allMigrations} is {@code true}
     * @param allMigrations {@code true} to roll back every executed migration
     * @param skipConfirmation {@code true} to skip the interactive confirmation prompt
     * @param dryRun {@code true} to display the plan without executing any rollback
     */
    public DownCommand(
            ExecutionContext context,
            @Nullable NodeId targetVersion,
            boolean allMigrations,
            boolean skipConfirmation,
            boolean dryRun) {
        this(
                context,
                targetVersion,
                allMigrations,
                skipConfirmation,
                dryRun,
                System.in,
                AnsiColor.isColorEnabled());
    }

    /**
     * Full constructor exposing the confirmation input stream and color flag, intended primarily
     * for testing.
     *
     * @param context the loaded execution context (graph, config, history)
     * @param targetVersion the node version to roll back (together with everything depending on
     *     it), or {@code null} when {@code allMigrations} is {@code true}
     * @param allMigrations {@code true} to roll back every executed migration
     * @param skipConfirmation {@code true} to skip the interactive confirmation prompt
     * @param dryRun {@code true} to display the plan without executing any rollback
     * @param inputStream the stream from which the confirmation answer is read
     * @param colorEnabled {@code true} to colorize console output
     */
    public DownCommand(
            ExecutionContext context,
            @Nullable NodeId targetVersion,
            boolean allMigrations,
            boolean skipConfirmation,
            boolean dryRun,
            InputStream inputStream,
            boolean colorEnabled) {
        this.context = context;
        this.targetVersion = targetVersion;
        this.allMigrations = allMigrations;
        this.skipConfirmation = skipConfirmation;
        this.dryRun = dryRun;
        this.inputStream = inputStream;
        this.colorEnabled = colorEnabled;
    }

    @Override
    public int execute() {
        try {
            // 1. Validate the arguments.
            if (!allMigrations) {
                if (targetVersion == null) {
                    System.err.println("Error: Either --all or target version must be specified.");
                    return 1;
                }
                if (context.graph().getNode(targetVersion).isEmpty()) {
                    System.err.println("Error: Target version not found: " + targetVersion.value());
                    return 1;
                }
            }

            // 2. Obtain the HistoryRepository.
            HistoryRepository historyRepo = context.createHistoryRepository();
            historyRepo.initialize();

            // 3. Create the executor and listener.
            ConsoleExecutionListener listener = new ConsoleExecutionListener(colorEnabled);
            DagExecutor executor =
                    new DagExecutor(
                            context.graph(), historyRepo, listener, ExecutionDirection.DOWN, 1);

            // 4. Decide what to roll back, and whether anything refuses the run.
            DownPlan plan =
                    new DownService(context.graph(), historyRepo)
                            .plan(targetVersion, allMigrations);
            DownBlocker blocker = plan.blocker();
            if (blocker != null) {
                DownPlanFormatter.format(blocker).forEach(System.err::println);
                return 1;
            }

            boolean leftFrozen = plan.leftFrozen();
            if (leftFrozen) {
                DownPlanFormatter.formatFrozen(plan).forEach(System.err::println);
            }

            Set<NodeId> targetNodes = plan.targetNodes();

            if (targetNodes.isEmpty()) {
                if (!leftFrozen) {
                    System.out.println("No migrations to rollback.");
                }
                return leftFrozen ? 1 : 0;
            }

            // 5. Build the reverse execution plan and display the graph.
            ExecutionPlan executionPlan =
                    TopologicalSort.createReverseExecutionPlanFor(context.graph(), targetNodes);
            displayRollbackPlan(context, executionPlan, historyRepo);

            // 6. Stop here in dry-run mode.
            if (dryRun) {
                System.out.println();
                System.out.println("No changes made (dry run).");
                return 0;
            }

            // 7. Confirmation prompt (skipped with -y).
            if (!skipConfirmation && !confirmRollback()) {
                System.out.println("Rollback cancelled.");
                return 0;
            }

            // 8. Execute the rollback.
            System.out.println();
            System.out.println("Executing rollback...");
            System.out.println();

            ExecutionResult result = executor.execute(targetNodes);
            return result.success() && !leftFrozen ? 0 : 1;

        } catch (Exception e) {
            System.err.println("Rollback failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    /** Renders the rollback plan as a reversed ASCII graph with per-node status markers. */
    private void displayRollbackPlan(
            ExecutionContext context, ExecutionPlan plan, HistoryRepository historyRepo) {
        String prefix = dryRun ? "[DRY RUN] " : "";
        String verb = dryRun ? "would be" : "will be";

        System.out.println();
        System.out.println(prefix + "Migrations to rollback:");
        System.out.println();

        // Filter the plan's nodes into DFS order.
        List<MigrationNode> sortedNodes = plan.filterNodesInOrder(context.nodes());

        // Render the graph using ExecutionGraphView (reversed mode).
        ExecutionGraphView graphView = new ExecutionGraphView(sortedNodes, true);
        List<String> lines =
                graphView.renderLines(
                        node -> {
                            String status =
                                    historyRepo.wasExecuted(node.id(), node.environment().id())
                                            ? "[✓]"
                                            : "[ ]";
                            return status + " " + node.id().value() + " - " + node.name();
                        });
        for (String line : lines) {
            System.out.println(line);
        }

        System.out.println();
        int total = plan.totalNodes();
        System.out.println(
                total + " migration" + (total == 1 ? "" : "s") + " " + verb + " rolled back.");
    }

    /** Displays the confirmation prompt and returns whether the user approved the rollback. */
    private boolean confirmRollback() {
        System.out.println();
        System.out.print("Proceed with rollback? [y/N]: ");
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
            String input = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
            return "y".equals(input) || "yes".equals(input);
        }
    }
}
