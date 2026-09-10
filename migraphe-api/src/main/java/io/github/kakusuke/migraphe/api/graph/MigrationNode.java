package io.github.kakusuke.migraphe.api.graph;

import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.task.Task;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A single node (a migration step) in a migration graph.
 *
 * <p>A {@code MigrationNode} couples structural metadata (its identity, name, owning target and
 * dependencies) with the {@link Task tasks} that perform the actual work. Nodes form a directed
 * acyclic graph (DAG) via their {@link #dependencies() dependencies}; Migraphe topologically sorts
 * the graph and executes nodes in dependency order when migrating forward, and in reverse order
 * when rolling back.
 *
 * <p>Plugins implement this interface to define a concrete migration step. Implementations are
 * expected to be immutable and to return stable values; the {@link #id()} must uniquely identify
 * the node within its graph. A node always provides an {@link #upTask() up task}, and optionally a
 * {@link #downTask() down task} for rollback support.
 *
 * @see Task
 * @see NodeId
 * @see Target
 */
public interface MigrationNode {

    /**
     * Returns the unique identifier of this node.
     *
     * @return the stable, non-{@code null} identifier used to reference this node and to declare
     *     dependencies on it
     */
    NodeId id();

    /**
     * Returns the human-readable name of this node.
     *
     * @return the display name of the migration step
     */
    String name();

    /**
     * Returns an optional human-readable description of this node.
     *
     * @return the description, or {@code null} if none was provided
     */
    @Nullable String description();

    /**
     * Returns the target this node belongs to.
     *
     * @return the owning {@link Target} against which this node's tasks execute
     */
    Target target();

    /**
     * Returns the identifiers of the nodes this node directly depends on.
     *
     * <p>During a forward migration, all dependencies must have completed successfully before this
     * node runs. The returned set defines this node's incoming edges in the DAG.
     *
     * @return the set of dependency identifiers, possibly empty but never {@code null}
     */
    Set<NodeId> dependencies();

    /**
     * Returns the task that applies this migration step (the forward direction).
     *
     * @return the up {@link Task}, never {@code null}
     */
    Task upTask();

    /**
     * Returns the task that rolls back this migration step (the reverse direction).
     *
     * @return the down {@link Task}, or {@code null} if this node does not support rollback
     */
    @Nullable Task downTask();

    /**
     * Returns an opaque token over what this node would apply, by handing the given {@link
     * Fingerprinter} the signatures that describe it.
     *
     * <p>Every plugin supplies one. There is no {@code default} deliberately: an inherited opt-out
     * would mean an absent token had two possible causes — a plugin that declines, and a record
     * written before the history had a column to hold one — and only the second has a remedy.
     *
     * <p>An implementation decides <em>what</em> describes it and hands those over; the {@link
     * Fingerprinter} decides how that becomes a token. So there is nothing here about digests,
     * about delimiting one part from the next, or about the dependencies this node stands on — a
     * node cannot get those wrong because it never handles them. Typically the signatures are its
     * tasks': one when there is no rollback, two when there is.
     *
     * <p>{@code null} is not "this implementation has nothing to offer". It is reserved for a node
     * that stands for something already recorded and finds no token there — core's own adapter over
     * a history row is the case that exists. A plugin describing a task file always has content.
     *
     * <p>Callers must <strong>report</strong> a changed token, never auto-remediate it. The remedy
     * is a destructive roll-back-and-re-apply, so a report has to stay declinable.
     *
     * @param fingerprinter folds the signatures, and holds the closure this node stands on
     * @return the fingerprint, or {@code null} only when this node stands for a record that carries
     *     none
     */
    @Nullable String fingerprint(Fingerprinter fingerprinter);

    /**
     * Returns why this node cannot be rolled back, or {@code null} if it can be.
     *
     * <p>Answers a question {@link #downTask()} alone cannot: a {@code null} down task means either
     * that the author declared the migration one-way or that they forgot to write the rollback, and
     * those call for opposite responses. A non-null reason here says it was declared, and is quoted
     * back to the operator when a rollback has to leave this node standing.
     *
     * <p>The default returns {@code null}, so a plugin that does not model the distinction reports
     * every missing rollback as an omission.
     *
     * @return the author's reason, or {@code null} if the node is not declared one-way
     */
    default @Nullable String noWayBack() {
        return null;
    }

    /**
     * Indicates whether this node is a root node (one with no dependencies).
     *
     * @return {@code true} if {@link #dependencies()} is empty, {@code false} otherwise
     */
    default boolean hasNoDependencies() {
        return dependencies().isEmpty();
    }

    /**
     * Indicates whether this node directly depends on the given node.
     *
     * @param nodeId the identifier of the potential dependency
     * @return {@code true} if {@link #dependencies()} contains {@code nodeId}, {@code false}
     *     otherwise
     */
    default boolean dependsOn(NodeId nodeId) {
        return dependencies().contains(nodeId);
    }
}
