package io.github.migraphe.core.history;

import io.github.migraphe.api.environment.EnvironmentId;
import io.github.migraphe.api.graph.NodeId;
import io.github.migraphe.api.history.ExecutionRecord;
import io.github.migraphe.api.history.ExecutionStatus;
import io.github.migraphe.api.history.HistoryRepository;
import java.util.*;
import java.util.stream.Collectors;

/**
 * メモリ内でマイグレーション履歴を管理する実装。
 *
 * <p>複数の環境の履歴を同時に保持できる。 アプリケーション再起動時には履歴が失われる。
 */
public final class InMemoryHistoryRepository implements HistoryRepository {

    private final Map<EnvironmentId, List<ExecutionRecord>> recordsByEnvironment;

    public InMemoryHistoryRepository() {
        this.recordsByEnvironment = new HashMap<>();
    }

    @Override
    public void initialize() {
        // メモリ内実装では初期化処理は不要
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

        return getRecordsForEnvironment(environmentId).stream()
                .anyMatch(r -> r.nodeId().equals(nodeId) && r.status() == ExecutionStatus.SUCCESS);
    }

    @Override
    public List<NodeId> executedNodes(EnvironmentId environmentId) {
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        return getRecordsForEnvironment(environmentId).stream()
                .filter(r -> r.status() == ExecutionStatus.SUCCESS)
                .map(ExecutionRecord::nodeId)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ExecutionRecord> findLatestRecord(NodeId nodeId, EnvironmentId environmentId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        return getRecordsForEnvironment(environmentId).stream()
                .filter(r -> r.nodeId().equals(nodeId))
                .max(Comparator.comparing(ExecutionRecord::executedAt));
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
