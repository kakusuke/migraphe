package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.cli.listener.ConsoleExecutionListener;
import io.github.kakusuke.migraphe.cli.util.AnsiColor;
import io.github.kakusuke.migraphe.core.config.ProjectConfig;
import io.github.kakusuke.migraphe.core.execution.DagExecutor;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.execution.ExecutionResult;
import io.github.kakusuke.migraphe.core.execution.Executor;
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
 * The {@code up} command, which executes forward (UP) migrations.
 *
 * <p>Resolves the set of pending nodes (optionally bounded by a target node id), renders the
 * execution plan as a graph, prompts for confirmation unless skipped, and then runs the migrations
 * via a {@link DagExecutor} in the {@link ExecutionDirection#UP} direction. In dry-run mode the
 * plan is displayed but nothing is executed.
 */
public class UpCommand implements Command {

    private final ExecutionContext context;
    private final @Nullable NodeId targetId;
    private final boolean skipConfirmation;
    private final boolean dryRun;
    private final InputStream inputStream;
    private final boolean colorEnabled;

    /**
     * Creates a command that migrates the entire graph with confirmation enabled.
     *
     * <p>Equivalent to no target id, no confirmation skipping, and no dry run, reading confirmation
     * input from {@link System#in} and auto-detecting color support.
     *
     * @param context the loaded execution context (graph, config, history)
     */
    public UpCommand(ExecutionContext context) {
        this(context, null, false, false, System.in, AnsiColor.isColorEnabled());
    }

    /**
     * Creates a command with the given options, reading confirmation from {@link System#in} and
     * auto-detecting color support.
     *
     * @param context the loaded execution context (graph, config, history)
     * @param targetId the highest node to migrate up to and including, or {@code null} to migrate
     *     all pending nodes
     * @param skipConfirmation {@code true} to skip the interactive confirmation prompt
     * @param dryRun {@code true} to display the plan without executing any migration
     */
    public UpCommand(
            ExecutionContext context,
            @Nullable NodeId targetId,
            boolean skipConfirmation,
            boolean dryRun) {
        this(context, targetId, skipConfirmation, dryRun, System.in, AnsiColor.isColorEnabled());
    }

    /**
     * Full constructor exposing the confirmation input stream and color flag, intended primarily
     * for testing.
     *
     * @param context the loaded execution context (graph, config, history)
     * @param targetId the highest node to migrate up to and including, or {@code null} to migrate
     *     all pending nodes
     * @param skipConfirmation {@code true} to skip the interactive confirmation prompt
     * @param dryRun {@code true} to display the plan without executing any migration
     * @param inputStream the stream from which the confirmation answer is read
     * @param colorEnabled {@code true} to colorize console output
     */
    public UpCommand(
            ExecutionContext context,
            @Nullable NodeId targetId,
            boolean skipConfirmation,
            boolean dryRun,
            InputStream inputStream,
            boolean colorEnabled) {
        this.context = context;
        this.targetId = targetId;
        this.skipConfirmation = skipConfirmation;
        this.dryRun = dryRun;
        this.inputStream = inputStream;
        this.colorEnabled = colorEnabled;
    }

    @Override
    public int execute() {
        try {
            // 1. When a target is specified, verify the node exists.
            if (targetId != null && context.graph().getNode(targetId).isEmpty()) {
                System.err.println("Error: Target not found: " + targetId.value());
                return 1;
            }

            // 2. Obtain the HistoryRepository.
            HistoryRepository historyRepo = context.createHistoryRepository();
            historyRepo.initialize();

            // 3. Create the executor and listener.
            ConsoleExecutionListener listener = new ConsoleExecutionListener(colorEnabled);
            Executor executor = createExecutor(context, historyRepo, listener);

            // 4. Determine the nodes to execute.
            Set<NodeId> targetNodes = executor.determineTargetNodes(targetId);

            if (targetNodes.isEmpty()) {
                System.out.println("No migrations to execute. All migrations are up to date.");
                return 0;
            }

            // 5. Build the ExecutionPlan and display the graph.
            ExecutionPlan plan =
                    TopologicalSort.createExecutionPlanFor(context.graph(), targetNodes);
            displayMigrationGraph(context, plan, historyRepo);

            // 6. Stop here in dry-run mode.
            if (dryRun) {
                System.out.println();
                System.out.println("No changes made (dry run).");
                return 0;
            }

            // 7. Confirmation prompt (skipped with -y).
            if (!skipConfirmation && !confirmExecution()) {
                System.out.println("Migration cancelled.");
                return 0;
            }

            // 8. Execute the migrations.
            System.out.println();
            System.out.println("Executing migrations...");
            System.out.println();

            ExecutionResult result = executor.execute(targetNodes);
            return result.success() ? 0 : 1;

        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    /** Creates the executor according to the project's execution configuration. */
    private Executor createExecutor(
            ExecutionContext context,
            HistoryRepository historyRepo,
            ConsoleExecutionListener listener) {
        ProjectConfig projectConfig = context.config().getConfigMapping(ProjectConfig.class);
        ProjectConfig.ExecutionSection execConfig = projectConfig.execution();
        int maxParallelism = execConfig.parallel() ? execConfig.maxParallelism() : 1;
        return new DagExecutor(
                context.graph(), historyRepo, listener, ExecutionDirection.UP, maxParallelism);
    }

    /** Renders the migration execution plan as an ASCII graph with per-node status markers. */
    private void displayMigrationGraph(
            ExecutionContext context, ExecutionPlan plan, HistoryRepository historyRepo) {
        String prefix = dryRun ? "[DRY RUN] " : "";
        String verb = dryRun ? "would be" : "will be";

        System.out.println();
        System.out.println(prefix + "Migrations to execute:");
        System.out.println();

        // Filter the plan's nodes into DFS order.
        List<MigrationNode> sortedNodes = plan.filterNodesInOrder(context.nodes());

        // Render the graph using ExecutionGraphView.
        ExecutionGraphView graphView = new ExecutionGraphView(sortedNodes);
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
                total + " migration" + (total == 1 ? "" : "s") + " " + verb + " executed.");
    }

    /** Displays the confirmation prompt and returns whether the user approved execution. */
    private boolean confirmExecution() {
        System.out.println();
        System.out.print("Proceed? [y/N]: ");
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
            String input = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
            return "y".equals(input) || "yes".equals(input);
        }
    }
}
