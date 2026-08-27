package io.github.kakusuke.migraphe.core.execution.support;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@link HistoryRepository#record} が例外を投げるリポジトリを作るためのラッパー。
 *
 * <p>マイグレーション自体は適用できたのに履歴の書き込みだけが失敗する状況——接続が切れた、制約に当たった——を再現する。読み取りは委譲先がそのまま応えるので、「書き込みだけが壊れている」という壊れ方だけを切り出せる。
 */
public record ThrowingHistoryRepository(HistoryRepository delegate) implements HistoryRepository {

    @Override
    public void initialize() {
        delegate.initialize();
    }

    @Override
    public void record(ExecutionRecord executionRecord) {
        throw new IllegalStateException("history connection lost");
    }

    @Override
    public boolean wasExecuted(NodeId nodeId, EnvironmentId environmentId) {
        return delegate.wasExecuted(nodeId, environmentId);
    }

    @Override
    public List<NodeId> executedNodes(EnvironmentId environmentId) {
        return delegate.executedNodes(environmentId);
    }

    @Override
    public @Nullable ExecutionRecord findLatestRecord(NodeId nodeId, EnvironmentId environmentId) {
        return delegate.findLatestRecord(nodeId, environmentId);
    }

    @Override
    public List<ExecutionRecord> allRecords(EnvironmentId environmentId) {
        return delegate.allRecords(environmentId);
    }
}
