package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.graph.FormatUtils;
import io.github.kakusuke.migraphe.core.graph.layout.ExecutionGraphView;
import java.util.List;
import org.gradle.api.tasks.TaskAction;

/** マイグレーションの実行状況を表示する Gradle タスク。 */
public abstract class MigrapheStatusTask extends AbstractMigrapheTask {

    @TaskAction
    public void status() {
        withExecutionContext(
                context -> {
                    getLogger().lifecycle("Migration Status");
                    getLogger().lifecycle("================");
                    getLogger().lifecycle("");

                    HistoryRepository historyRepo = context.createHistoryRepository();
                    historyRepo.initialize();

                    ExecutionGraphView graphView = new ExecutionGraphView(context.graph(), false);

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

    /** 副作用のあるタスクはキャッシュしない。 */
    public MigrapheStatusTask() {
        getOutputs().upToDateWhen(task -> false);
    }
}
