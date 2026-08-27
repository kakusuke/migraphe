package io.github.kakusuke.migraphe.core.execution.support;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 「適用済みか」の判定だけが委譲先と食い違うリポジトリを作るためのラッパー。
 *
 * <p>`HistoryRepository` は `wasExecuted` と `findLatestRecord`
 * が整合することをどこにも要求していない。同梱の2実装はどちらも同じ行を見るので 食い違わないが、サードパーティ実装は別基準を使える。その状況を再現する。
 */
public record AlwaysAppliedHistoryRepository(HistoryRepository delegate)
        implements HistoryRepository {

    @Override
    public void initialize() {
        delegate.initialize();
    }

    @Override
    public void record(ExecutionRecord executionRecord) {
        delegate.record(executionRecord);
    }

    @Override
    public boolean wasExecuted(NodeId nodeId, EnvironmentId environmentId) {
        return true;
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
