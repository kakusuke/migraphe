package io.github.kakusuke.migraphe.api.spi;

import java.util.List;
import java.util.Optional;

/**
 * Base interface for a plugin's task configuration.
 *
 * <p>A task definition is the configuration view of a single migration task as declared in the
 * project's YAML. Each {@link MigraphePlugin} declares a concrete subtype via {@link
 * MigraphePlugin#taskDefinitionClass()} and implements it as a SmallRye {@code @ConfigMapping}
 * interface so its fields bind directly from YAML. The runtime then hands the bound definition to
 * {@link MigrationNodeProvider#createNode(io.github.kakusuke.migraphe.api.graph.NodeId,
 * TaskDefinition, java.util.Set, io.github.kakusuke.migraphe.api.environment.Environment)} to build
 * a graph node.
 *
 * <p>Because SmallRye Config's {@code @ConfigMapping} represents optional properties as {@link
 * Optional}, this interface exposes optional fields as {@link Optional} values rather than nullable
 * references.
 *
 * @param <T> the type of the UP/DOWN action (for example {@code String}, an SQL statement, for
 *     SQL-based plugins such as PostgreSQL)
 * @see MigraphePlugin#taskDefinitionClass()
 * @see MigrationNodeProvider
 */
public interface TaskDefinition<T> {

    /**
     * Returns the task's name.
     *
     * @return the human-readable task name
     */
    String name();

    /**
     * Returns the task's optional description.
     *
     * @return an {@link Optional} containing the description, or an empty {@link Optional} if none
     *     was configured
     */
    Optional<String> description();

    /**
     * Returns the ID of the target this task runs against.
     *
     * @return the target ID
     */
    String target();

    /**
     * Returns the IDs of the tasks this task depends on.
     *
     * @return an {@link Optional} containing the list of dependency task IDs, or an empty {@link
     *     Optional} if the task has no declared dependencies
     */
    Optional<List<String>> dependencies();

    /**
     * Returns the UP (forward) migration action.
     *
     * @return the action to apply when migrating forward
     */
    T up();

    /**
     * Returns the optional DOWN (rollback) migration action.
     *
     * @return an {@link Optional} containing the rollback action, or an empty {@link Optional} if
     *     the task does not support rollback
     */
    Optional<T> down();

    /**
     * Returns why this migration cannot be rolled back, when the author declared that it cannot.
     *
     * <p>Answers a question {@link #down()} alone cannot: an absent rollback means either that the
     * author decided the migration is one-way or that nobody has written it yet, and only one of
     * those is something to leave alone. A task must supply one or the other.
     *
     * <p>The default returns empty, so a plugin that does not model the distinction reports every
     * absent rollback as an omission.
     *
     * @return an {@link Optional} containing the reason, or empty when the migration is reversible
     */
    default Optional<String> noWayBack() {
        return Optional.empty();
    }
}
