package io.github.migraphe.core.history;

import io.github.migraphe.core.environment.EnvironmentId;
import io.github.migraphe.core.graph.NodeId;
import java.util.*;

/** マイグレーション実行履歴の集約ルート。 環境ごとに実行されたマイグレーションの監査証跡を保持する。 */
public final class MigrationHistory {
    private final EnvironmentId environmentId;
    private final List<ExecutionRecord> records;

    private MigrationHistory(EnvironmentId environmentId) {
        this.environmentId =
                Objects.requireNonNull(environmentId, "environmentId must not be null");
        this.records = new ArrayList<>();
    }

    /** 実行記録を追加する。 */
    public void record(ExecutionRecord record) {
        if (!record.environmentId().equals(environmentId)) {
            throw new IllegalArgumentException(
                    "Record environment mismatch: expected "
                            + environmentId
                            + ", got "
                            + record.environmentId());
        }
        records.add(record);
    }

    /** 指定されたノードが成功実行済みかどうか。 */
    public boolean wasExecuted(NodeId nodeId) {
        return records.stream()
                .anyMatch(r -> r.nodeId().equals(nodeId) && r.status() == ExecutionStatus.SUCCESS);
    }

    /** 成功実行済みノードのIDリストを取得。 */
    public List<NodeId> executedNodes() {
        return records.stream()
                .filter(r -> r.status() == ExecutionStatus.SUCCESS)
                .map(ExecutionRecord::nodeId)
                .toList();
    }

    /** 指定されたノードの最新の実行記録を取得。 */
    public Optional<ExecutionRecord> findLatestRecord(NodeId nodeId) {
        return records.stream()
                .filter(r -> r.nodeId().equals(nodeId))
                .max(Comparator.comparing(ExecutionRecord::executedAt));
    }

    /** 全ての実行記録を取得（時系列順）。 */
    public List<ExecutionRecord> allRecords() {
        return List.copyOf(records);
    }

    public EnvironmentId environmentId() {
        return environmentId;
    }

    public int recordCount() {
        return records.size();
    }

    public static MigrationHistory forEnvironment(EnvironmentId environmentId) {
        return new MigrationHistory(environmentId);
    }
}
