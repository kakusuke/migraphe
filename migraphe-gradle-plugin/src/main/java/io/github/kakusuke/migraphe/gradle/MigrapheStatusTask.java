package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.execution.StatusService;
import io.github.kakusuke.migraphe.core.execution.StatusService.NodeStatus;
import io.github.kakusuke.migraphe.core.execution.StatusService.StatusInfo;
import io.github.kakusuke.migraphe.core.graph.FormatUtils;
import io.github.kakusuke.migraphe.core.graph.layout.ExecutionGraphView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Gradle task that displays the migration execution status.
 *
 * <p>Registered as {@code migrapheStatus} by {@link MigrapheGradlePlugin}, the task renders the
 * migration graph and, for each node, shows whether it has already been executed (with its latest
 * duration and timestamp) or is still pending, followed by a summary count.
 */
@DisableCachingByDefault(
        because = "Migraphe tasks have side effects and their output cannot be cached")
public abstract class MigrapheStatusTask extends AbstractMigrapheTask {

    /**
     * Task action that prints the execution status of every migration node.
     *
     * <p>Loads the execution context, initializes the history repository, and renders the graph
     * annotating each node as executed or pending, with a closing total/executed/pending summary.
     */
    @TaskAction
    public void status() {
        withExecutionContext(
                context -> {
                    getLogger().lifecycle("Migration Status");
                    getLogger().lifecycle("================");
                    getLogger().lifecycle("");

                    HistoryRepository historyRepo = context.createHistoryRepository();
                    historyRepo.initialize();

                    StatusInfo status = new StatusService(context.graph(), historyRepo).getStatus();
                    Map<NodeId, NodeStatus> statusByNode = new HashMap<>();
                    for (NodeStatus nodeStatus : status.nodes()) {
                        statusByNode.put(nodeStatus.node().id(), nodeStatus);
                    }

                    ExecutionGraphView graphView = new ExecutionGraphView(context.graph());

                    List<String> lines =
                            graphView.renderLines(
                                    node -> {
                                        NodeStatus nodeStatus =
                                                Objects.requireNonNull(
                                                        statusByNode.get(node.id()),
                                                        "graph node missing from status: "
                                                                + node.id().value());
                                        StringBuilder sb = new StringBuilder();
                                        sb.append(nodeStatus.executed() ? "[✓] " : "[ ] ");
                                        sb.append(node.id().value())
                                                .append(" - ")
                                                .append(node.name());
                                        ExecutionRecord record = nodeStatus.latestRecord();
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
                                        return sb.toString();
                                    });

                    for (String line : lines) {
                        getLogger().lifecycle(line);
                    }

                    getLogger().lifecycle("");
                    getLogger()
                            .lifecycle(
                                    "Summary: Total: {} | Executed: {} | Pending: {}",
                                    status.executedCount() + status.pendingCount(),
                                    status.executedCount(),
                                    status.pendingCount());
                });
    }

    /** Creates the task and marks it as never up to date, since it has side effects. */
    public MigrapheStatusTask() {
        getOutputs().upToDateWhen(task -> false);
    }
}
