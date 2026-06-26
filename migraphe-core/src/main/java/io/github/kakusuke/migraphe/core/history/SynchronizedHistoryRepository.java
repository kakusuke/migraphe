package io.github.kakusuke.migraphe.core.history;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Thread-safe {@link HistoryRepository} decorator that serializes every operation on the delegate.
 *
 * <p>{@link io.github.kakusuke.migraphe.core.execution.DagExecutor} reads prior state and records
 * results from many concurrent virtual threads. This wrapper guards each method with {@code
 * synchronized (delegate)} so that a delegate that is not itself thread-safe (for example {@link
 * InMemoryHistoryRepository}) is accessed by at most one thread at a time. {@code DagExecutor}
 * applies this wrapper automatically unless the supplied repository is already an instance of this
 * class.
 */
public final class SynchronizedHistoryRepository implements HistoryRepository {

    private final HistoryRepository delegate;

    /**
     * Wraps a repository so that all of its operations are mutually exclusive.
     *
     * @param delegate the repository to which all calls are forwarded under synchronization
     */
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
