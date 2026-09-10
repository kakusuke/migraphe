package io.github.kakusuke.migraphe.api.target;

/**
 * A connection migrations are executed against — one entry under {@code targets/}, such as {@code
 * db1}.
 *
 * <p>A {@code Target} represents a single, configured destination (typically a database or piece of
 * infrastructure) against which {@link io.github.kakusuke.migraphe.api.task.Task tasks} run. Every
 * {@link io.github.kakusuke.migraphe.api.graph.MigrationNode} names exactly one, and every history
 * row records the one its migration was applied to.
 *
 * <p>A target is <strong>not</strong> a deployment environment. {@code environments/<name>.yaml},
 * selected by {@code --env}, is a configuration overlay: it rewrites a target's values while the
 * target's name stays the same. Nor is it "the node a run was aimed at" — that is a {@link
 * io.github.kakusuke.migraphe.api.graph.NodeId}.
 *
 * <p>Plugins implement this interface to model a concrete target (for example, a JDBC connection).
 * Implementations are expected to be immutable and to expose a stable {@link #id()} that uniquely
 * identifies the target within a project.
 *
 * @see TargetId
 * @see io.github.kakusuke.migraphe.api.graph.MigrationNode
 */
public interface Target {

    /**
     * Returns the unique identifier of this target.
     *
     * @return the stable, non-{@code null} identifier used to distinguish this target from others
     *     and to partition migration history
     */
    TargetId id();

    /**
     * Returns the human-readable name of this target.
     *
     * @return the display name, for example {@code "db1"}
     */
    String name();
}
