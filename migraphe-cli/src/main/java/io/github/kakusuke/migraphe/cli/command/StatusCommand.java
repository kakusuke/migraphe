package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.graph.FormatUtils;
import io.github.kakusuke.migraphe.core.graph.layout.ExecutionGraphView;
import java.util.List;

/**
 * The {@code status} command, which reports the execution state of every migration.
 *
 * <p>Renders the whole migration graph and, for each node, marks whether it has been executed
 * against its environment. Executed nodes additionally show the most recent run's duration and
 * timestamp. A summary line tallies the total, executed, and pending counts.
 */
public class StatusCommand implements Command {

    private final ExecutionContext context;

    /**
     * Creates the status command.
     *
     * @param context the loaded execution context (graph, config, history)
     */
    public StatusCommand(ExecutionContext context) {
        this.context = context;
    }

    @Override
    public int execute() {
        try {
            System.out.println("Migration Status");
            System.out.println("================");
            System.out.println();

            // Obtain the HistoryRepository.
            HistoryRepository historyRepo = context.createHistoryRepository();
            historyRepo.initialize();

            // Render the graph.
            ExecutionGraphView graphView = new ExecutionGraphView(context.graph());

            int[] executedCount = {0};
            int[] pendingCount = {0};

            List<String> lines =
                    graphView.renderLines(
                            node -> {
                                boolean executed =
                                        historyRepo.wasExecuted(node.id(), node.environment().id());
                                StringBuilder sb = new StringBuilder();
                                if (executed) {
                                    executedCount[0]++;
                                    sb.append("[✓] ");
                                } else {
                                    pendingCount[0]++;
                                    sb.append("[ ] ");
                                }
                                sb.append(node.id().value()).append(" - ").append(node.name());
                                if (executed) {
                                    ExecutionRecord record =
                                            historyRepo.findLatestRecord(
                                                    node.id(), node.environment().id());
                                    if (record != null) {
                                        sb.append(" (")
                                                .append(
                                                        FormatUtils.formatDuration(
                                                                record.durationMs()))
                                                .append(", ")
                                                .append(
                                                        FormatUtils.formatDateTime(
                                                                record.executedAt()))
                                                .append(")");
                                    }
                                }
                                return sb.toString();
                            });

            for (String line : lines) {
                System.out.println(line);
            }

            System.out.println();

            // Summary.
            int total = executedCount[0] + pendingCount[0];
            System.out.println(
                    "Summary: Total: "
                            + total
                            + " | Executed: "
                            + executedCount[0]
                            + " | Pending: "
                            + pendingCount[0]);

            return 0; // success

        } catch (Exception e) {
            System.err.println("Failed to get migration status: " + e.getMessage());
            e.printStackTrace();
            return 1; // error exit
        }
    }
}
