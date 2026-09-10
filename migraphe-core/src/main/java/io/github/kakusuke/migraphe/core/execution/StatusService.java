package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.Fingerprinter;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

    private static final Comparator<ExecutionRecord> BY_RECENCY =
            Comparator.comparing(ExecutionRecord::executedAt).thenComparing(ExecutionRecord::id);

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
        Map<NodeId, List<ExecutionRecord>> recordsByNode = rowsByNode();
        Map<NodeId, ExecutionRecord> appliedRows = appliedRows();
        List<NodeStatus> nodeStatuses = new ArrayList<>();
        int executedCount = 0;
        int pendingCount = 0;

        for (MigrationNode node : graph.allNodes()) {
            boolean executed = historyRepository.wasExecuted(node.id());
            ExecutionRecord latestRecord = null;
            ExecutionRecord appliedRecord = null;

            if (executed) {
                List<ExecutionRecord> records = recordsByNode.getOrDefault(node.id(), List.of());
                if (!records.isEmpty()) {
                    latestRecord = records.get(records.size() - 1);
                }
                appliedRecord = appliedRows.get(node.id());
                executedCount++;
            } else {
                pendingCount++;
            }

            nodeStatuses.add(
                    new NodeStatus(
                            node,
                            executed,
                            latestRecord,
                            appliedRecord,
                            graph.fingerprinterFor(node.id())));
        }

        return new StatusInfo(nodeStatuses, executedCount, pendingCount, findOrphans(appliedRows));
    }

    /**
     * Finds the nodes the history says are applied but the definitions no longer declare.
     *
     * <p>This asks the repository what it holds rather than only asking about nodes already known
     * from the graph, which is why these were invisible: nothing looked. Nothing is enumerated to
     * find them either — the rows are the whole input, and each orphan is placed in the target its
     * own row names. Filtering by the targets the declared nodes happen to name would hide a
     * migration the moment the last task pointing at its target was deleted, which is the case this
     * exists to show.
     */
    private List<OrphanStatus> findOrphans(Map<NodeId, ExecutionRecord> appliedRows) {
        Set<NodeId> declared = new HashSet<>();
        for (MigrationNode node : graph.allNodes()) {
            declared.add(node.id());
        }

        List<OrphanStatus> orphans = new ArrayList<>();
        for (NodeId applied : historyRepository.executedNodes()) {
            if (declared.contains(applied)) {
                continue;
            }
            ExecutionRecord row = appliedRows.get(applied);
            if (row != null) {
                orphans.add(new OrphanStatus(applied, row.targetId(), row));
            }
        }
        return List.copyOf(orphans);
    }

    /**
     * The row that applied each migration, as the repository reports it.
     *
     * <p>{@link HistoryRepository#latestApplies} rather than a fold of this class's own: the rule
     * is the repository's to state, and an implementation that can answer it from its store answers
     * for every caller rather than for the ones that remembered to ask. It is a different question
     * from the newest row of <em>any</em> kind — a failed rollback is the newest thing a migration
     * has and applied nothing — which is why {@code latestRecord} is read separately.
     *
     * <p>Keyed by identifier alone, which is what {@link HistoryRepository#latestApplies} already
     * returns, so the merge below is a guard rather than a rule: an implementation that overrides
     * that read and hands back two rows for one identifier is contradicting its contract, and this
     * takes the newest rather than whichever the map happened to yield.
     */
    private Map<NodeId, ExecutionRecord> appliedRows() {
        Map<NodeId, ExecutionRecord> rows = new HashMap<>();
        for (ExecutionRecord apply : historyRepository.latestApplies()) {
            rows.merge(
                    apply.nodeId(),
                    apply,
                    (existing, incoming) ->
                            BY_RECENCY.compare(incoming, existing) > 0 ? incoming : existing);
        }
        return rows;
    }

    /**
     * Every row the history holds, per migration, oldest first.
     *
     * <p>Keyed by identifier and not by target: an identifier is unique across the project, so the
     * rows describing a migration are the rows carrying its id, wherever they were written. Reading
     * them per target loses a migration whose {@code target:} was edited — its rows stay where they
     * were written and the node no longer points there.
     *
     * <p>The order is imposed rather than assumed. {@link HistoryRepository#allRecords} declares
     * none and the shipped implementations differ, so the last element is the newest only because
     * this sorts by {@code executedAt} and then by {@code id} — the tie-break that saves a driver
     * dropping sub-second precision.
     */
    private Map<NodeId, List<ExecutionRecord>> rowsByNode() {
        Map<NodeId, List<ExecutionRecord>> rows = new HashMap<>();
        for (ExecutionRecord record : historyRepository.allRecords()) {
            rows.computeIfAbsent(record.nodeId(), id -> new ArrayList<>()).add(record);
        }
        for (List<ExecutionRecord> perNode : rows.values()) {
            perNode.sort(BY_RECENCY);
        }
        return rows;
    }

    /**
     * A node the history says is applied but the definitions no longer declare.
     *
     * @param nodeId the identifier the history recorded
     * @param targetId the target it was applied against
     * @param appliedRecord the record that applied it, or {@code null} when the history holds none
     *     that can be read as such
     */
    public record OrphanStatus(
            NodeId nodeId, TargetId targetId, @Nullable ExecutionRecord appliedRecord) {}

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
     * @param fingerprinter folds this node's fingerprint, holding the closure it is folded over
     */
    public record NodeStatus(
            MigrationNode node,
            boolean executed,
            @Nullable ExecutionRecord latestRecord,
            @Nullable ExecutionRecord appliedRecord,
            Fingerprinter fingerprinter) {

        /**
         * Classifies the node's current content against the content that was applied.
         *
         * <p>What is compared is whatever the node hands its {@link Fingerprinter}, which is the
         * plugin's choice; this only compares the two tokens.
         *
         * @return the comparison outcome; see {@link UpContentState} for what each value means
         */
        public UpContentState upContentState() {
            if (appliedRecord == null) {
                return UpContentState.NOT_APPLICABLE;
            }
            String current;
            try {
                current = node.fingerprint(fingerprinter);
            } catch (RuntimeException e) {
                return UpContentState.UNREADABLE;
            }
            if (current == null) {
                // Both ways of not supplying a token land here: throwing, and answering with none.
                // Supplying one is part of the plugin contract, and the single null the design
                // keeps is core's own adapter over a history row — so a declared node answering
                // null is out of contract, and no amend fills a row from a definition with nothing
                // to give. Reading it as "nothing to compare" put this row back among the states a
                // repair reaches, and left `status` calling agreement what the other three
                // commands refuse to run on.
                return UpContentState.UNREADABLE;
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
         * Reports whether every node is applied from its current content and nothing is orphaned.
         *
         * <p>Only {@link UpContentState#UNCHANGED} counts as agreement. This answers an
         * <em>assertion</em> — the {@code --check} form of {@code status} — not a report, and the
         * states meaning "the comparison could not be made" (a row predating the fingerprint
         * column, a plugin that supplies none, an accessor that threw) are not evidence that the
         * two sides match. Calling them agreement would be the tool guessing on the side that
         * happens to keep a pipeline green.
         *
         * <p>It lives here rather than in either front end because both ask it, and a rule about
         * what counts as drift is not something the CLI and the Gradle task may answer differently.
         *
         * @return {@code true} when nothing differs
         */
        public boolean everythingAgrees() {
            if (!orphans.isEmpty()) {
                return false;
            }
            for (NodeStatus nodeStatus : nodes) {
                if (nodeStatus.upContentState() != UpContentState.UNCHANGED) {
                    return false;
                }
            }
            return true;
        }

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
