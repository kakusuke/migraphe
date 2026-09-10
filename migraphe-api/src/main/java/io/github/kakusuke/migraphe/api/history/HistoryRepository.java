package io.github.kakusuke.migraphe.api.history;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Abstraction over the persistence of migration execution history.
 *
 * <p>Migraphe consults a {@code HistoryRepository} to decide which nodes have already run (so they
 * can be skipped on a subsequent up) and to retrieve the serialized down task needed for a
 * rollback. There is one history per project, and a migration's identifier is unique within it, so
 * no read takes a {@link TargetId}: each row records the target it was applied against as data
 * about the migration, the way it records the fingerprint.
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
     * Reports whether the history has been created in this store yet.
     *
     * <p>Asked before anything else touches the history, so that a command reporting on migrations
     * does not create a table as a side effect of being run. Only {@code init} — and {@code up},
     * which is the moment a project's history should come into being — calls {@link #initialize()};
     * everything else refuses and names {@code init}.
     *
     * <p><strong>No default</strong>, for the reason {@link #upgrades()} has one and this does not:
     * "yes" is not a correct answer for a backend that has not been asked. One that forgot to
     * override would report itself ready and then fail on its first read, with an error naming a
     * missing table rather than a missing step. A backend whose store cannot be absent — an
     * in-memory one — answers {@code true} deliberately.
     *
     * @return {@code true} when the history is there to be read and written
     */
    boolean isInitialized();

    /**
     * Returns the upgrades this history needs to reach the shape this version writes, in order.
     *
     * <p>{@link #initialize()} creates a history with every column this version writes and never
     * alters an existing one, so a fresh project needs nothing from this list. Everything that
     * changes a history an older release created lives here instead, behind the upgrade command an
     * operator schedules — a history shared with a deployment still on the older version must not
     * have a column dropped out from under it by whoever runs {@code status} first.
     *
     * <p>The list belongs to the repository rather than being collected from every plugin on the
     * classpath. There is one history per project, fixed by {@code history.target}, and its
     * upgrades are written in that backend's dialect against that backend's shape; a sweep would
     * hand a MySQL upgrade to a PostgreSQL history whenever a project uses both.
     *
     * <p>The default is empty because "nothing" is a correct answer here: a new backend has no
     * older shapes to come from. That is why this has a default and {@link
     * io.github.kakusuke.migraphe.api.graph.MigrationNode#fingerprint} does not — a node always has
     * content to fold, so a default there would let an implementation silently skip it.
     *
     * @return the ordered upgrades, or an empty list when this backend has none
     */
    default List<HistoryUpgrade> upgrades() {
        return List.of();
    }

    /**
     * Persists an execution record.
     *
     * @param record the execution record to store
     */
    void record(ExecutionRecord record);

    /**
     * Reports whether the given migration is currently applied.
     *
     * <p>No target argument: a migration's identifier is unique across the project, so a row for it
     * is a row about it wherever it was applied. The target a row carries says where the objects
     * are, not which migration it is.
     *
     * <p>The applied state is decided by the migration's <strong>most recent successful</strong>
     * record: applied if that record is an UP, not applied if it is a DOWN, and not applied if
     * there is no successful record at all. Records whose status is not SUCCESS never change the
     * applied state — a rollback that failed leaves the migration applied, because nothing was
     * undone.
     *
     * @param nodeId the identifier of the migration to check
     * @return {@code true} if the migration is currently applied, {@code false} otherwise
     */
    boolean wasExecuted(NodeId nodeId);

    /**
     * Returns the identifiers of every migration currently applied.
     *
     * <p>This is the set form of {@link #wasExecuted} and must agree with it for every migration —
     * including in taking no target: an identifier is unique across the project, so which target
     * holds a migration's rows is not part of asking whether it is applied.
     *
     * @return the identifiers of the currently applied migrations, possibly empty
     */
    List<NodeId> executedNodes();

    /**
     * Returns the most recent execution record for the given node, whatever target wrote it.
     *
     * <p>No target argument, for the reason {@link #wasExecuted} takes none: an identifier is
     * unique across the project, so the row asked for is the newest one carrying that identifier.
     * The row names the target it was written against.
     *
     * @param nodeId the identifier of the node whose latest record is requested
     * @return the latest {@link ExecutionRecord}, or {@code null} if none exists
     */
    @Nullable ExecutionRecord findLatestRecord(NodeId nodeId);

    /**
     * Returns every execution record this history holds, whatever target it was written against.
     *
     * <p>No target argument: every row carries its own {@link ExecutionRecord#targetId()}, so a
     * caller that wants one target's rows filters them, and a caller that has to read across
     * targets — reconstructing the graph the history describes, or asking where a migration is
     * applied — can. Asking per target could not answer the second question without knowing the
     * targets in advance, which is exactly what the history is being consulted about.
     *
     * @return every execution record, possibly empty
     */
    List<ExecutionRecord> allRecords();

    /**
     * The row that applied each migration still standing, in the target it stands in.
     *
     * <p>An <em>apply</em>, not the latest record of any kind: only an {@link
     * io.github.kakusuke.migraphe.api.task.ExecutionDirection#UP} that succeeded carries the
     * fingerprint, the dependencies and the rollback payload, so a migration whose newest row is a
     * failed rollback still has to be read from the row that applied it.
     *
     * <p><strong>One row per identifier.</strong> An identifier is unique across the project, so
     * this walks each identifier's successful rows in order and keeps the placement standing at the
     * end. Keying by {@code (node, target)} instead would report a migration rolled back and
     * re-applied elsewhere as standing in two places at once, because the rollback supersedes
     * nothing outside its own pair. Failures change nothing here, exactly as they change nothing
     * for {@link #wasExecuted}: a rollback that failed leaves the migration applied.
     *
     * <p>The walk is also where a malformed history is caught, and what it judges by is the
     * <strong>origin</strong>, never the target. A second {@link ExecutionOrigin#EXECUTED} apply
     * landing while the migration already stands is refused: {@code up} skips an identifier it
     * finds applied, so no run writes one now. An {@link ExecutionOrigin#AMENDED} apply is a claim
     * rather than an execution and supersedes what stands, which is how {@code amend} states what
     * the definition says now — including a definition that has moved to another target.
     *
     * <p>Later rows win, ordered by {@link ExecutionRecord#executedAt()} and then by {@link
     * ExecutionRecord#id()}. The tie-break is not decoration: identifiers are time-ordered, and a
     * driver that drops sub-second precision leaves a rollback and the re-apply after it sharing an
     * instant, where taking either would otherwise be left to the storage engine.
     *
     * <p>Derived from {@link #allRecords()} so every implementation answers alike; one that can ask
     * its store more cheaply may override it, as long as it answers the same.
     *
     * @return one record per applied migration, in no particular order
     * @throws IllegalStateException if a migration was executed again while it already stood — a
     *     second {@link ExecutionOrigin#EXECUTED} apply with no rollback between, which no run
     *     writes now, though a history carried over from before the reads were keyed by the
     *     identifier can hold one
     */
    default List<ExecutionRecord> latestApplies() {
        Comparator<ExecutionRecord> byRecency =
                Comparator.comparing(ExecutionRecord::executedAt)
                        .thenComparing(ExecutionRecord::id);
        Map<NodeId, List<ExecutionRecord>> successesByNode = new HashMap<>();
        for (ExecutionRecord record : allRecords()) {
            if (record.status() == ExecutionStatus.SUCCESS) {
                successesByNode
                        .computeIfAbsent(record.nodeId(), id -> new ArrayList<>())
                        .add(record);
            }
        }

        List<ExecutionRecord> applies = new ArrayList<>();
        for (List<ExecutionRecord> successes : successesByNode.values()) {
            successes.sort(byRecency);
            ExecutionRecord standing = null;
            for (ExecutionRecord record : successes) {
                if (record.direction() != ExecutionDirection.UP) {
                    standing = null;
                    continue;
                }
                if (standing != null && record.origin() == ExecutionOrigin.EXECUTED) {
                    throw appliedWhileAlreadyStanding(standing, record);
                }
                standing = record;
            }
            if (standing != null) {
                applies.add(standing);
            }
        }
        return List.copyOf(applies);
    }

    /**
     * Refuses a history in which a migration was applied again while it already stood.
     *
     * <p>No run writes this <em>now</em>: {@code up} asks whether the identifier is applied, sees
     * that it is, and does not apply it again — wherever the task points now. A history carried
     * over from before the reads were keyed by the identifier can hold it, because the question was
     * then asked per target and re-pointing a task's {@code target:} made {@code up} apply the same
     * migration a second time. So the message says what the rows are and stops; it does not claim
     * how they got there, and it prescribes no repair, because the history store is the plugin's
     * and core cannot know what editing it looks like.
     *
     * <p>What tells the legitimate case apart is the <strong>origin</strong>, not the target. An
     * {@link ExecutionOrigin#AMENDED} row is a claim rather than an execution — {@code amend}
     * stating what the definition says now — so it may supersede a standing placement, including
     * one against another target. And an apply after a rollback is ordinary whatever target either
     * row names, because the rollback is what took the migration out.
     */
    private static IllegalStateException appliedWhileAlreadyStanding(
            ExecutionRecord standing, ExecutionRecord incoming) {
        return new IllegalStateException(
                standing.nodeId().value()
                        + " is recorded as applied twice with no rollback between:\n  "
                        + describe(standing)
                        + "\n  "
                        + describe(incoming)
                        + "\nA migration stands in one place, so migraphe cannot say where this one"
                        + " is. Which of these rows should not be there is a question about the"
                        + " rows themselves, and no migraphe command removes one.");
    }

    /** One row, in the terms an operator can look it up by. */
    private static String describe(ExecutionRecord record) {
        return record.id()
                + "  target="
                + record.targetId().value()
                + "  at="
                + record.executedAt()
                + "  origin="
                + record.origin();
    }
}
