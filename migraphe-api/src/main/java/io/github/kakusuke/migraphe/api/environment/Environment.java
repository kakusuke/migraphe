package io.github.kakusuke.migraphe.api.environment;

/**
 * A target environment in which migrations are executed, such as {@code dev}, {@code staging}, or
 * {@code prod}.
 *
 * <p>An {@code Environment} represents a single, configured destination (typically a database or
 * piece of infrastructure) against which {@link io.github.kakusuke.migraphe.api.task.Task tasks}
 * run. Every {@link io.github.kakusuke.migraphe.api.graph.MigrationNode} is bound to exactly one
 * environment, and migration history is partitioned per environment so that the same migration can
 * be tracked independently across destinations.
 *
 * <p>Plugins implement this interface to model a concrete environment (for example, a JDBC
 * connection). Implementations are expected to be immutable and to expose a stable {@link #id()}
 * that uniquely identifies the environment within a project.
 *
 * @see EnvironmentId
 * @see io.github.kakusuke.migraphe.api.graph.MigrationNode
 */
public interface Environment {

    /**
     * Returns the unique identifier of this environment.
     *
     * @return the stable, non-{@code null} identifier used to distinguish this environment from
     *     others and to partition migration history
     */
    EnvironmentId id();

    /**
     * Returns the human-readable name of this environment.
     *
     * @return the display name, for example {@code "dev"}, {@code "staging"}, or {@code "prod"}
     */
    String name();
}
