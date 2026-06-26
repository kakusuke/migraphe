package io.github.kakusuke.migraphe.api.task;

/**
 * The direction in which a migration is executed.
 *
 * <p>Migraphe traverses the migration graph in dependency order for {@link #UP} and in reverse
 * dependency order for {@link #DOWN}. The direction also determines which {@link Task} a {@link
 * io.github.kakusuke.migraphe.api.graph.MigrationNode} runs.
 *
 * @see io.github.kakusuke.migraphe.api.graph.MigrationNode
 */
public enum ExecutionDirection {
    /** A forward migration that applies a step. */
    UP,

    /** A reverse migration that rolls a step back. */
    DOWN
}
