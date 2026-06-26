package io.github.kakusuke.migraphe.api.graph;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.task.Task;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A single node (a migration step) in a migration graph.
 *
 * <p>A {@code MigrationNode} couples structural metadata (its identity, name, owning environment
 * and dependencies) with the {@link Task tasks} that perform the actual work. Nodes form a directed
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
 * @see Environment
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
     * Returns the environment this node belongs to.
     *
     * @return the owning {@link Environment} against which this node's tasks execute
     */
    Environment environment();

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
