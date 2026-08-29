package io.github.kakusuke.migraphe.core.history;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.ExecutionStatus;
import io.github.kakusuke.migraphe.api.history.HistoryFingerprintUpdater;
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
 * restarts. This implementation is used in tests and by the {@code noop} plugin, whose {@link
 * io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider} returns it for every environment.
 * It is deliberately <strong>not</strong> reachable as a fallback for a misconfigured {@code
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
public final class InMemoryHistoryRepository
        implements HistoryRepository, HistoryFingerprintUpdater {

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

    /**
     * Replaces the fingerprint stored on one record, leaving every other field as it was.
     *
     * <p>Record ids are unique across environments, so every partition is searched.
     *
     * @param recordId the id of the record to revise
     * @param fingerprint the fingerprint to store
     * @return {@code true} if a record with that id was revised, {@code false} if none matched
     */
    @Override
    public boolean updateFingerprint(String recordId, String fingerprint) {
        Objects.requireNonNull(recordId, "recordId must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");

        for (List<ExecutionRecord> records : recordsByEnvironment.values()) {
            for (int i = 0; i < records.size(); i++) {
                ExecutionRecord existing = records.get(i);
                if (existing.id().equals(recordId)) {
                    records.set(i, withFingerprint(existing, fingerprint));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean wasExecuted(NodeId nodeId, EnvironmentId environmentId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        // The latest successful record decides; non-SUCCESS records change nothing.
        return getRecordsForEnvironment(environmentId).stream()
                .filter(r -> r.nodeId().equals(nodeId))
                .filter(r -> r.status() == ExecutionStatus.SUCCESS)
                .max(Comparator.comparing(ExecutionRecord::executedAt))
                .map(r -> r.direction() == ExecutionDirection.UP)
                .orElse(false);
    }

    @Override
    public List<NodeId> executedNodes(EnvironmentId environmentId) {
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        // Return only nodes whose latest successful record is an UP.
        Map<NodeId, ExecutionRecord> latestByNode = new HashMap<>();
        for (ExecutionRecord r : getRecordsForEnvironment(environmentId)) {
            if (r.status() != ExecutionStatus.SUCCESS) {
                continue;
            }
            latestByNode.merge(
                    r.nodeId(),
                    r,
                    (existing, incoming) ->
                            incoming.executedAt().isAfter(existing.executedAt())
                                    ? incoming
                                    : existing);
        }

        return latestByNode.values().stream()
                .filter(r -> r.direction() == ExecutionDirection.UP)
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

    private static ExecutionRecord withFingerprint(ExecutionRecord record, String fingerprint) {
        return new ExecutionRecord(
                record.id(),
                record.nodeId(),
                record.environmentId(),
                record.direction(),
                record.status(),
                record.executedAt(),
                record.description(),
                record.serializedDownTask(),
                record.durationMs(),
                record.errorMessage(),
                fingerprint);
    }
}
