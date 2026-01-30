package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.graph.ExecutionGraphView;
import io.github.kakusuke.migraphe.core.graph.FormatUtils;
import io.github.kakusuke.migraphe.core.graph.NodeLineInfo;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.tasks.TaskAction;

/** マイグレーションの実行状況を表示する Gradle タスク。 */
public abstract class MigrapheStatusTask extends AbstractMigrapheTask {

    @TaskAction
    public void status() {
        ExecutionContext context = loadExecutionContext();

        getLogger().lifecycle("Migration Status");
        getLogger().lifecycle("================");
        getLogger().lifecycle("");

        HistoryRepository historyRepo = context.createHistoryRepository();
        historyRepo.initialize();

        List<MigrationNode> sortedNodes = new ArrayList<>(context.nodes());
        ExecutionGraphView graphView = new ExecutionGraphView(sortedNodes, false);
        List<NodeLineInfo> lines = graphView.lines();

        int executedCount = 0;
        int pendingCount = 0;

        for (NodeLineInfo info : lines) {
            MigrationNode node = info.node();
            boolean executed = historyRepo.wasExecuted(node.id(), node.environment().id());

            if (info.mergeLine() != null) {
                getLogger().lifecycle(info.mergeLine());
            }

            String statusMark = executed ? "[✓]" : "[ ]";
            StringBuilder nodeLineBuilder = new StringBuilder();
            nodeLineBuilder
                    .append(info.graphPrefix())
                    .append(" ")
                    .append(statusMark)
                    .append(" ")
                    .append(node.id().value())
                    .append(" - ")
                    .append(node.name());

            if (executed) {
                executedCount++;
                ExecutionRecord record =
                        historyRepo.findLatestRecord(node.id(), node.environment().id());
                if (record != null) {
                    nodeLineBuilder
                            .append(" (")
                            .append(FormatUtils.formatDuration(record.durationMs()))
                            .append(", ")
                            .append(FormatUtils.formatDateTime(record.executedAt()))
                            .append(")");
                }
            } else {
                pendingCount++;
            }

            getLogger().lifecycle(nodeLineBuilder.toString());

            if (info.branchLine() != null) {
                getLogger().lifecycle(info.branchLine());
            }

            if (info.connectorLine() != null) {
                getLogger().lifecycle(info.connectorLine());
            }
        }

        getLogger().lifecycle("");
        getLogger()
                .lifecycle(
                        "Summary: Total: {} | Executed: {} | Pending: {}",
                        executedCount + pendingCount,
                        executedCount,
                        pendingCount);
    }

    /** 副作用のあるタスクはキャッシュしない。 */
    public MigrapheStatusTask() {
        getOutputs().upToDateWhen(task -> false);
    }
}
