package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.graph.FormatUtils;
import io.github.kakusuke.migraphe.core.graph.layout.ExecutionGraphView;
import java.util.List;
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

                    ExecutionGraphView graphView = new ExecutionGraphView(context.graph());

                    int[] executedCount = {0};
                    int[] pendingCount = {0};

                    List<String> lines =
                            graphView.renderLines(
                                    node -> {
                                        boolean executed =
                                                historyRepo.wasExecuted(
                                                        node.id(), node.environment().id());
                                        StringBuilder sb = new StringBuilder();
                                        if (executed) {
                                            executedCount[0]++;
                                            sb.append("[✓] ");
                                        } else {
                                            pendingCount[0]++;
                                            sb.append("[ ] ");
                                        }
                                        sb.append(node.id().value())
                                                .append(" - ")
                                                .append(node.name());
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
                        getLogger().lifecycle(line);
                    }

                    getLogger().lifecycle("");
                    getLogger()
                            .lifecycle(
                                    "Summary: Total: {} | Executed: {} | Pending: {}",
                                    executedCount[0] + pendingCount[0],
                                    executedCount[0],
                                    pendingCount[0]);
                });
    }

    /** Creates the task and marks it as never up to date, since it has side effects. */
    public MigrapheStatusTask() {
        getOutputs().upToDateWhen(task -> false);
    }
}
