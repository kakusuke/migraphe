package io.github.kakusuke.migraphe.api.history;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.target.TargetId;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Abstraction over the persistence of migration execution history.
 *
 * <p>Migraphe consults a {@code HistoryRepository} to decide which nodes have already run (so they
 * can be skipped on a subsequent up) and to retrieve the serialized down task needed for a
 * rollback. History is always partitioned by {@link TargetId}, so the same migration can be tracked
 * independently across targets.
 *
 * <p>Plugins implement this interface to support different backends (in-memory, JDBC/PostgreSQL/
 * MySQL, files, object storage, and so on). Implementations are not required to be thread-safe;
 * Migraphe wraps a repository in a synchronized decorator when running migrations in parallel.
 *
 * @see ExecutionRecord
 * @see TargetId
 * @see NodeId
 */
public interface HistoryRepository {

    /**
     * Prepares the repository for use.
     *
     * <p>Depending on the backend this may create the history schema or table, create a file,
     * verify a bucket, or perform any other one-time setup. It is called before any other method.
     */
    void initialize();

    /**
     * Persists an execution record.
     *
     * @param record the execution record to store
     */
    void record(ExecutionRecord record);

    /**
     * Reports whether the given node has already been executed successfully in the given target.
     *
     * @param nodeId the identifier of the node to check
     * @param targetId the target whose history is consulted
     * @return {@code true} if a successful execution is recorded for the node in the target, {@code
     *     false} otherwise
     */
    boolean wasExecuted(NodeId nodeId, TargetId targetId);

    /**
     * Returns the identifiers of nodes that have executed successfully in the given target.
     *
     * @param targetId the target whose history is consulted
     * @return the list of successfully executed node identifiers, possibly empty
     */
    List<NodeId> executedNodes(TargetId targetId);

    /**
     * Returns the most recent execution record for the given node in the given target.
     *
     * @param nodeId the identifier of the node whose latest record is requested
     * @param targetId the target whose history is consulted
     * @return the latest {@link ExecutionRecord}, or {@code null} if none exists
     */
    @Nullable ExecutionRecord findLatestRecord(NodeId nodeId, TargetId targetId);

    /**
     * Returns every execution record for the given target.
     *
     * @param targetId the target whose history is consulted
     * @return the list of all execution records for the target, possibly empty
     */
    List<ExecutionRecord> allRecords(TargetId targetId);
}
