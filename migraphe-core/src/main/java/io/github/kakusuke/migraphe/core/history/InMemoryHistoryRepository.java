package io.github.kakusuke.migraphe.core.history;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.ExecutionStatus;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import java.util.*;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * In-memory {@link HistoryRepository} that keeps execution records in heap memory.
 *
 * <p>Records are partitioned per {@link EnvironmentId}, so the history of multiple environments can
 * be held simultaneously. Because nothing is persisted, all history is lost when the application
 * restarts. This implementation is used in tests and as the fallback when a project configures no
 * history target (see {@link
 * io.github.kakusuke.migraphe.core.execution.ExecutionContext#createHistoryRepository()}).
 *
 * <p>This class is not itself thread-safe; concurrent callers should wrap it in a {@link
 * SynchronizedHistoryRepository}.
 *
 * <p>"Applied" semantics: a node is considered applied only when its most recent record for the
 * environment is an {@link ExecutionDirection#UP} record with status {@link
 * ExecutionStatus#SUCCESS}.
 */
public final class InMemoryHistoryRepository implements HistoryRepository {

    private final Map<EnvironmentId, List<ExecutionRecord>> recordsByEnvironment;

    /** Creates an empty in-memory history repository. */
    public InMemoryHistoryRepository() {
        this.recordsByEnvironment = new HashMap<>();
    }

    @Override
    public void initialize() {
        // No initialization is needed for the in-memory implementation.
    }

    @Override
    public void record(ExecutionRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        recordsByEnvironment
                .computeIfAbsent(record.environmentId(), k -> new ArrayList<>())
                .add(record);
    }

    @Override
    public boolean wasExecuted(NodeId nodeId, EnvironmentId environmentId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        // Take the latest record; treat the node as applied only if it is UP and SUCCESS.
        return getRecordsForEnvironment(environmentId).stream()
                .filter(r -> r.nodeId().equals(nodeId))
                .max(Comparator.comparing(ExecutionRecord::executedAt))
                .map(
                        r ->
                                r.direction() == ExecutionDirection.UP
                                        && r.status() == ExecutionStatus.SUCCESS)
                .orElse(false);
    }

    @Override
    public List<NodeId> executedNodes(EnvironmentId environmentId) {
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        // Return only nodes whose latest record is UP and SUCCESS.
        Map<NodeId, ExecutionRecord> latestByNode = new HashMap<>();
        for (ExecutionRecord r : getRecordsForEnvironment(environmentId)) {
            latestByNode.merge(
                    r.nodeId(),
                    r,
                    (existing, incoming) ->
                            incoming.executedAt().isAfter(existing.executedAt())
                                    ? incoming
                                    : existing);
        }

        return latestByNode.values().stream()
                .filter(
                        r ->
                                r.direction() == ExecutionDirection.UP
                                        && r.status() == ExecutionStatus.SUCCESS)
                .map(ExecutionRecord::nodeId)
                .collect(Collectors.toList());
    }

    @Override
    public @Nullable ExecutionRecord findLatestRecord(NodeId nodeId, EnvironmentId environmentId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        return getRecordsForEnvironment(environmentId).stream()
                .filter(r -> r.nodeId().equals(nodeId))
                .max(Comparator.comparing(ExecutionRecord::executedAt))
                .orElse(null);
    }

    @Override
    public List<ExecutionRecord> allRecords(EnvironmentId environmentId) {
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        return List.copyOf(getRecordsForEnvironment(environmentId));
    }

    private List<ExecutionRecord> getRecordsForEnvironment(EnvironmentId environmentId) {
        return recordsByEnvironment.getOrDefault(environmentId, Collections.emptyList());
    }
}
