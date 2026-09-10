package io.github.kakusuke.migraphe.core.history;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.ExecutionStatus;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import java.util.*;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * In-memory {@link HistoryRepository} that keeps execution records in heap memory.
 *
 * <p>Records are partitioned per {@link TargetId}, so the history of multiple targets can be held
 * simultaneously. Because nothing is persisted, all history is lost when the application restarts.
 * This implementation is used in tests and by the {@code noop} plugin, whose {@link
 * io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider} returns it for every target. It is
 * deliberately <strong>not</strong> reachable as a fallback for a misconfigured {@code
 * history.target}: a run that applied its migrations and then discarded the record would be worse
 * than one that refused to start.
 *
 * <p>This class is not itself thread-safe; concurrent callers should wrap it in a {@link
 * SynchronizedHistoryRepository}.
 *
 * <p>"Applied" semantics: a node is considered applied when its most recent record with status
 * {@link ExecutionStatus#SUCCESS} is an {@link ExecutionDirection#UP}. Records that failed or were
 * skipped never change the applied state, so a rollback that failed leaves the node applied.
 */
public final class InMemoryHistoryRepository implements HistoryRepository {

    /**
     * Recency, the way the whole family defines it: {@code executedAt}, then the identifier.
     *
     * <p>The tie-break is not decoration. Identifiers are time-ordered, and a driver that drops
     * sub-second precision leaves two rows of one node sharing an instant — a rollback and the
     * re-apply after it, say. Without it the older row wins and the node reads as not applied.
     */
    private static final Comparator<ExecutionRecord> BY_RECENCY =
            Comparator.comparing(ExecutionRecord::executedAt).thenComparing(ExecutionRecord::id);

    private final Map<TargetId, List<ExecutionRecord>> recordsByTarget;

    /** Creates an empty in-memory history repository. */
    public InMemoryHistoryRepository() {
        this.recordsByTarget = new HashMap<>();
    }

    @Override
    public void initialize() {
        // No initialization is needed for the in-memory implementation.
    }

    /**
     * Always {@code true}: the store is this object's own map, so it exists as soon as the
     * repository does and no run can find it absent.
     */
    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public void record(ExecutionRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        recordsByTarget.computeIfAbsent(record.targetId(), k -> new ArrayList<>()).add(record);
    }

    @Override
    public boolean wasExecuted(NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        // The latest successful record decides; non-SUCCESS records change nothing.
        return allRecords().stream()
                .filter(r -> r.nodeId().equals(nodeId))
                .filter(r -> r.status() == ExecutionStatus.SUCCESS)
                .max(BY_RECENCY)
                .map(r -> r.direction() == ExecutionDirection.UP)
                .orElse(false);
    }

    @Override
    public List<NodeId> executedNodes() {
        // Return only nodes whose latest successful record is an UP.
        Map<NodeId, ExecutionRecord> latestByNode = new HashMap<>();
        for (ExecutionRecord r : allRecords()) {
            if (r.status() != ExecutionStatus.SUCCESS) {
                continue;
            }
            latestByNode.merge(
                    r.nodeId(),
                    r,
                    (existing, incoming) ->
                            BY_RECENCY.compare(incoming, existing) > 0 ? incoming : existing);
        }

        return latestByNode.values().stream()
                .filter(r -> r.direction() == ExecutionDirection.UP)
                .map(ExecutionRecord::nodeId)
                .collect(Collectors.toList());
    }

    @Override
    public @Nullable ExecutionRecord findLatestRecord(NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return allRecords().stream()
                .filter(r -> r.nodeId().equals(nodeId))
                .max(BY_RECENCY)
                .orElse(null);
    }

    @Override
    public List<ExecutionRecord> allRecords() {
        return recordsByTarget.values().stream().flatMap(List::stream).toList();
    }
}
