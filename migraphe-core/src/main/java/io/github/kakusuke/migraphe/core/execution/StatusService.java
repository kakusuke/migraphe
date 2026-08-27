package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Computes the applied/pending status of every node in a migration graph.
 *
 * <p>Used by the {@code status} command (CLI and Gradle) to report, for each node, whether it has
 * been applied and what its latest execution record was. It reads state from a {@link
 * HistoryRepository} but performs no mutation.
 */
public final class StatusService {

    private final MigrationGraph graph;
    private final HistoryRepository historyRepository;

    /**
     * Creates a status service over a graph and its history.
     *
     * @param graph the migration graph whose nodes are inspected
     * @param historyRepository the repository consulted for applied state and latest records
     */
    public StatusService(MigrationGraph graph, HistoryRepository historyRepository) {
        this.graph = graph;
        this.historyRepository = historyRepository;
    }

    /**
     * Computes the current status of all nodes in the graph.
     *
     * <p>For each node, queries the history repository for whether it has been applied and, if so,
     * its latest execution record, accumulating overall executed and pending counts.
     *
     * @return a {@link StatusInfo} holding per-node statuses and aggregate counts
     */
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

    /**
     * Status of a single migration node.
     *
     * @param node the migration node
     * @param executed {@code true} if the node has been successfully applied (UP)
     * @param latestRecord the node's most recent execution record, or {@code null} if it has never
     *     been executed
     */
    public record NodeStatus(
            MigrationNode node, boolean executed, @Nullable ExecutionRecord latestRecord) {

        /**
         * Classifies the node's current UP content against the content that was applied.
         *
         * <p>Only the UP content is covered, so editing a rollback definition or an apply-mode flag
         * does not show up here.
         *
         * @return the comparison outcome; see {@link UpContentState} for what each value means
         */
        public UpContentState upContentState() {
            if (latestRecord == null) {
                return UpContentState.NOT_APPLICABLE;
            }
            String current;
            try {
                current = node.fingerprint();
            } catch (RuntimeException e) {
                return UpContentState.UNREADABLE;
            }
            if (current == null) {
                return UpContentState.NOT_APPLICABLE;
            }
            String applied = latestRecord.fingerprint();
            if (applied == null) {
                return UpContentState.UNKNOWN;
            }
            return applied.equals(current) ? UpContentState.UNCHANGED : UpContentState.CHANGED;
        }

        /**
         * Indicates whether the node's UP content differs from what was applied.
         *
         * <p>Only {@link UpContentState#CHANGED} answers {@code true}: an unknown, unreadable or
         * inapplicable comparison is never reported as a change, because {@link
         * MigrationNode#fingerprint()} defines an absent token as "unknown" rather than
         * "unchanged". Callers that need to tell those apart should read {@link #upContentState()}.
         *
         * @return {@code true} only when both fingerprints are known and differ
         */
        public boolean upContentChanged() {
            return upContentState() == UpContentState.CHANGED;
        }
    }

    /**
     * Aggregate status across all nodes in the graph.
     *
     * @param nodes the per-node statuses
     * @param executedCount the number of nodes that have been applied
     * @param pendingCount the number of nodes not yet applied
     */
    public record StatusInfo(List<NodeStatus> nodes, int executedCount, int pendingCount) {

        /**
         * Returns the total number of nodes (executed plus pending).
         *
         * @return the total node count
         */
        public int totalCount() {
            return executedCount + pendingCount;
        }
    }
}
