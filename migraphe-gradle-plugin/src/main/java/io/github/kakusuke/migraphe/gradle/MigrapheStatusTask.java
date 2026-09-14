package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.execution.HistoryReadiness;
import io.github.kakusuke.migraphe.core.execution.RepairVocabulary;
import io.github.kakusuke.migraphe.core.execution.StatusLineFormatter;
import io.github.kakusuke.migraphe.core.execution.StatusService;
import io.github.kakusuke.migraphe.core.execution.StatusService.NodeStatus;
import io.github.kakusuke.migraphe.core.execution.StatusService.StatusInfo;
import io.github.kakusuke.migraphe.core.graph.layout.ExecutionGraphView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
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
     * Whether to fail the build unless the definitions and the history agree.
     *
     * @return the check property
     */
    @Input
    @Optional
    public abstract Property<Boolean> getCheck();

    /**
     * Turns the report into an assertion from the {@code --check} command line option.
     *
     * @param check {@code true} to fail the build while anything differs
     */
    @Option(option = "check", description = "Fail the build unless nothing differs")
    public void setCheckOption(boolean check) {
        getCheck().set(check);
    }

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
                    // Asked before the heading: a refusal under "Migration Status" reads as a
                    // status.
                    HistoryRepository historyRepo = context.createHistoryRepository();
                    List<String> notReady =
                            HistoryReadiness.refusal(historyRepo, RepairVocabulary.GRADLE);
                    if (!notReady.isEmpty()) {
                        throw new GradleException(String.join(System.lineSeparator(), notReady));
                    }

                    getLogger().lifecycle("Migration Status");
                    getLogger().lifecycle("================");
                    getLogger().lifecycle("");

                    StatusInfo status = new StatusService(context.graph(), historyRepo).getStatus();
                    Map<NodeId, NodeStatus> statusByNode = new HashMap<>();
                    for (NodeStatus nodeStatus : status.nodes()) {
                        statusByNode.put(nodeStatus.node().id(), nodeStatus);
                    }

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
                        getLogger().lifecycle(line);
                    }

                    for (String line : StatusLineFormatter.formatOrphans(status)) {
                        getLogger().lifecycle(line);
                    }

                    getLogger().lifecycle("");
                    getLogger()
                            .lifecycle(
                                    "Summary: Total: {} | Executed: {} | Pending: {}",
                                    status.executedCount() + status.pendingCount(),
                                    status.executedCount(),
                                    status.pendingCount());

                    // The rule for what counts as agreement lives in core, so this task and the
                    // CLI cannot answer it differently.
                    if (getCheck().getOrElse(false) && !status.everythingAgrees()) {
                        throw new GradleException(
                                "The definitions and the history do not agree. See the markers"
                                        + " above; migraphe amend resolves drift in the history's"
                                        + " favour.");
                    }
                });
    }

    /** Creates the task and marks it as never up to date, since it has side effects. */
    public MigrapheStatusTask() {
        getOutputs().upToDateWhen(task -> false);
    }
}
