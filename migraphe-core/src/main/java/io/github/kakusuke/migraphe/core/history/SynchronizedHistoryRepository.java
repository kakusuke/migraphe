package io.github.kakusuke.migraphe.core.history;

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
    public boolean wasExecuted(NodeId nodeId) {
        synchronized (delegate) {
            return delegate.wasExecuted(nodeId);
        }
    }

    @Override
    public List<NodeId> executedNodes() {
        synchronized (delegate) {
            return delegate.executedNodes();
        }
    }

    @Override
    public @Nullable ExecutionRecord findLatestRecord(NodeId nodeId) {
        synchronized (delegate) {
            return delegate.findLatestRecord(nodeId);
        }
    }

    @Override
    public List<ExecutionRecord> allRecords() {
        synchronized (delegate) {
            return delegate.allRecords();
        }
    }

    /**
     * Forwards rather than inheriting, which is what every method here is for.
     *
     * <p>{@link HistoryRepository#latestApplies()} has a default that folds {@link
     * HistoryRepository#allRecords()}, so leaving it out would still answer — with the delegate's
     * rows, but never with the delegate's own answer. A repository that overrides it to ask its
     * store directly would be silently bypassed for every caller that goes through this wrapper,
     * and {@code DagExecutor} wraps unconditionally. Every read this wrapper carries is forwarded
     * for that reason, not only the ones that happen to have an overriding implementation today.
     *
     * <p>Folding here instead of under the lock would also read the rows outside it.
     */
    @Override
    public List<ExecutionRecord> latestApplies() {
        synchronized (delegate) {
            return delegate.latestApplies();
        }
    }
}
