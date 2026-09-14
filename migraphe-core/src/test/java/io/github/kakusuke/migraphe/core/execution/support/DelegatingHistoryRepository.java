package io.github.kakusuke.migraphe.core.execution.support;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 全ての読み書きを委譲するリポジトリ。1つの問いだけを別の答えに差し替えるために継承する。
 *
 * <p>{@link HistoryRepository#latestApplies()} も明示的に転送する。既定実装は {@link
 * HistoryRepository#allRecords()} を畳むので、転送しなければ「委譲先の<em>行</em>」は返るが「委譲先の<em>答え</em>」は返らない — これは
 * {@code SynchronizedHistoryRepository} が本番側で塞いでいるのと同じ落とし穴で、テスト側でも同じ答えにする。
 */
public class DelegatingHistoryRepository implements HistoryRepository {

    private final HistoryRepository delegate;

    public DelegatingHistoryRepository(HistoryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public void initialize() {
        delegate.initialize();
    }

    @Override
    public void record(ExecutionRecord record) {
        delegate.record(record);
    }

    @Override
    public boolean wasExecuted(NodeId nodeId) {
        return delegate.wasExecuted(nodeId);
    }

    @Override
    public List<NodeId> executedNodes() {
        return delegate.executedNodes();
    }

    @Override
    public @Nullable ExecutionRecord findLatestRecord(NodeId nodeId) {
        return delegate.findLatestRecord(nodeId);
    }

    @Override
    public List<ExecutionRecord> allRecords() {
        return delegate.allRecords();
    }

    @Override
    public List<ExecutionRecord> latestApplies() {
        return delegate.latestApplies();
    }
}
