package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.ExecutionStatus;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        Map<EnvironmentId, List<ExecutionRecord>> recordsByEnvironment = new HashMap<>();
        List<NodeStatus> nodeStatuses = new ArrayList<>();
        int executedCount = 0;
        int pendingCount = 0;

        for (MigrationNode node : graph.allNodes()) {
            EnvironmentId environmentId = node.environment().id();
            boolean executed = historyRepository.wasExecuted(node.id(), environmentId);
            ExecutionRecord latestRecord = null;
            ExecutionRecord appliedRecord = null;

            if (executed) {
                List<ExecutionRecord> records =
                        recordsByEnvironment.computeIfAbsent(
                                environmentId, StatusService.this::orderedRecords);
                for (ExecutionRecord record : records) {
                    if (!record.nodeId().equals(node.id())) {
                        continue;
                    }
                    latestRecord = record;
                    if (record.direction() == ExecutionDirection.UP
                            && record.status() == ExecutionStatus.SUCCESS) {
                        appliedRecord = record;
                    }
                }
                executedCount++;
            } else {
                pendingCount++;
            }

            nodeStatuses.add(new NodeStatus(node, executed, latestRecord, appliedRecord));
        }

        return new StatusInfo(
                nodeStatuses, executedCount, pendingCount, findOrphans(recordsByEnvironment));
    }

    /**
     * Finds the nodes the history says are applied but the definitions no longer declare.
     *
     * <p>This is the first thing in the tree to ask the repository what it holds rather than only
     * asking about nodes already known from the graph, which is why these were invisible: nothing
     * looked. Their environments are those of the declared nodes, so a target whose configuration
     * was also deleted stays out of reach — the history is keyed by a target that can no longer be
     * resolved.
     */
    private List<OrphanStatus> findOrphans(
            Map<EnvironmentId, List<ExecutionRecord>> recordsByEnvironment) {
        Set<NodeId> declared = new HashSet<>();
        Set<EnvironmentId> environments = new LinkedHashSet<>();
        for (MigrationNode node : graph.allNodes()) {
            declared.add(node.id());
            environments.add(node.environment().id());
        }

        List<OrphanStatus> orphans = new ArrayList<>();
        for (EnvironmentId environmentId : environments) {
            for (NodeId applied : historyRepository.executedNodes(environmentId)) {
                if (declared.contains(applied)) {
                    continue;
                }
                List<ExecutionRecord> records =
                        recordsByEnvironment.computeIfAbsent(environmentId, this::orderedRecords);
                ExecutionRecord appliedRecord = null;
                for (ExecutionRecord record : records) {
                    if (record.nodeId().equals(applied)
                            && record.direction() == ExecutionDirection.UP
                            && record.status() == ExecutionStatus.SUCCESS) {
                        appliedRecord = record;
                    }
                }
                orphans.add(new OrphanStatus(applied, environmentId, appliedRecord));
            }
        }
        return List.copyOf(orphans);
    }

    /**
     * A node the history says is applied but the definitions no longer declare.
     *
     * @param nodeId the identifier the history recorded
     * @param environmentId the target it was applied against
     * @param appliedRecord the record that applied it, or {@code null} when the history holds none
     *     that can be read as such
     */
    public record OrphanStatus(
            NodeId nodeId, EnvironmentId environmentId, @Nullable ExecutionRecord appliedRecord) {}

    /**
     * Reads one environment's records oldest first.
     *
     * <p>{@link HistoryRepository#allRecords} declares no order, and the shipped implementations
     * differ — the JDBC one sorts, the in-memory one returns insertion order — so the order is
     * imposed here rather than assumed. Ties on {@code executedAt} are broken by {@code id}, which
     * sorts in creation order for records minted since 0.6.0.
     */
    private List<ExecutionRecord> orderedRecords(EnvironmentId environmentId) {
        List<ExecutionRecord> records =
                new ArrayList<>(historyRepository.allRecords(environmentId));
        records.sort(
                Comparator.comparing(ExecutionRecord::executedAt)
                        .thenComparing(ExecutionRecord::id));
        return records;
    }

    /**
     * Status of a single migration node.
     *
     * @param node the migration node
     * @param executed {@code true} if the node has been successfully applied (UP)
     * @param latestRecord the node's most recent execution record whatever its outcome, or {@code
     *     null} if it has never been executed. This is what last happened, which is not necessarily
     *     what put the node in its current state
     * @param appliedRecord the record that applied the node — its most recent successful UP — or
     *     {@code null} if it is not currently applied. Everything about the applied state is read
     *     from here, because a later failed record describes an attempt, not the state
     */
    public record NodeStatus(
            MigrationNode node,
            boolean executed,
            @Nullable ExecutionRecord latestRecord,
            @Nullable ExecutionRecord appliedRecord) {

        /**
         * Classifies the node's current UP content against the content that was applied.
         *
         * <p>Only the UP content is covered, so editing a rollback definition or an apply-mode flag
         * does not show up here.
         *
         * @return the comparison outcome; see {@link UpContentState} for what each value means
         */
        public UpContentState upContentState() {
            if (appliedRecord == null) {
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
            String applied = appliedRecord.fingerprint();
            if (applied == null) {
                return UpContentState.UNKNOWN;
            }
            return applied.equals(current) ? UpContentState.UNCHANGED : UpContentState.CHANGED;
        }
    }

    /**
     * Aggregate status across all nodes in the graph.
     *
     * @param nodes the per-node statuses
     * @param executedCount the number of nodes that have been applied
     * @param pendingCount the number of nodes not yet applied
     * @param orphans the nodes the history says are applied but the definitions no longer declare
     */
    public record StatusInfo(
            List<NodeStatus> nodes,
            int executedCount,
            int pendingCount,
            List<OrphanStatus> orphans) {

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
