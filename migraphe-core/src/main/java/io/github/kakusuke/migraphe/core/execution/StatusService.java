package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** マイグレーションのステータスを取得するサービス。 */
public final class StatusService {

    private final MigrationGraph graph;
    private final HistoryRepository historyRepository;

    public StatusService(MigrationGraph graph, HistoryRepository historyRepository) {
        this.graph = graph;
        this.historyRepository = historyRepository;
    }

    /** ステータス情報を取得する。 */
    public StatusInfo getStatus() {
        List<NodeStatus> nodeStatuses = new ArrayList<>();
        int executedCount = 0;
        int pendingCount = 0;

        for (MigrationNode node : graph.allNodes()) {
            boolean executed = historyRepository.wasExecuted(node.id(), node.environment().id());
            ExecutionRecord latestRecord = null;

            if (executed) {
                latestRecord =
                        historyRepository.findLatestRecord(node.id(), node.environment().id());
                executedCount++;
            } else {
                pendingCount++;
            }

            nodeStatuses.add(new NodeStatus(node, executed, latestRecord));
        }

        return new StatusInfo(nodeStatuses, executedCount, pendingCount);
    }

    /** ノードのステータス情報。 */
    public record NodeStatus(
            MigrationNode node, boolean executed, @Nullable ExecutionRecord latestRecord) {}

    /** 全体のステータス情報。 */
    public record StatusInfo(List<NodeStatus> nodes, int executedCount, int pendingCount) {

        /** 総ノード数を返す。 */
        public int totalCount() {
            return executedCount + pendingCount;
        }
    }
}
