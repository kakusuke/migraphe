package io.github.kakusuke.migraphe.core.history;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class SynchronizedHistoryRepository implements HistoryRepository {

    private final HistoryRepository delegate;

    public SynchronizedHistoryRepository(HistoryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public void initialize() {
        synchronized (delegate) {
            delegate.initialize();
        }
    }

    @Override
    public void record(ExecutionRecord record) {
        synchronized (delegate) {
            delegate.record(record);
        }
    }

    @Override
    public boolean wasExecuted(NodeId nodeId, EnvironmentId environmentId) {
        synchronized (delegate) {
            return delegate.wasExecuted(nodeId, environmentId);
        }
    }

    @Override
    public List<NodeId> executedNodes(EnvironmentId environmentId) {
        synchronized (delegate) {
            return delegate.executedNodes(environmentId);
        }
    }

    @Override
    public @Nullable ExecutionRecord findLatestRecord(NodeId nodeId, EnvironmentId environmentId) {
        synchronized (delegate) {
            return delegate.findLatestRecord(nodeId, environmentId);
        }
    }

    @Override
    public List<ExecutionRecord> allRecords(EnvironmentId environmentId) {
        synchronized (delegate) {
            return delegate.allRecords(environmentId);
        }
    }
}
