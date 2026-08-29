package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.execution.StatusLineFormatter;
import io.github.kakusuke.migraphe.core.execution.StatusService;
import io.github.kakusuke.migraphe.core.execution.StatusService.NodeStatus;
import io.github.kakusuke.migraphe.core.execution.StatusService.StatusInfo;
import io.github.kakusuke.migraphe.core.graph.layout.ExecutionGraphView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

            StatusInfo status = new StatusService(context.graph(), historyRepo).getStatus();
            Map<NodeId, NodeStatus> statusByNode = new HashMap<>();
            for (NodeStatus nodeStatus : status.nodes()) {
                statusByNode.put(nodeStatus.node().id(), nodeStatus);
            }

            // Render the graph.
            ExecutionGraphView graphView = new ExecutionGraphView(context.graph());

            List<String> lines =
                    graphView.renderLines(
                            node ->
                                    StatusLineFormatter.format(
                                            Objects.requireNonNull(
                                                    statusByNode.get(node.id()),
                                                    "graph node missing from status: "
                                                            + node.id().value())));

            for (String line : lines) {
                System.out.println(line);
            }

            for (String line : StatusLineFormatter.formatOrphans(status)) {
                System.out.println(line);
            }

            System.out.println();

            // Summary.
            int total = status.executedCount() + status.pendingCount();
            System.out.println(
                    "Summary: Total: "
                            + total
                            + " | Executed: "
                            + status.executedCount()
                            + " | Pending: "
                            + status.pendingCount());

            return 0; // success

        } catch (Exception e) {
            System.err.println("Failed to get migration status: " + e.getMessage());
            e.printStackTrace();
            return 1; // error exit
        }
    }
}
