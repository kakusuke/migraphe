package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import java.util.List;
import java.util.Optional;

/**
 * SQL-based {@link TaskDefinition} used by the JDBC plugin family.
 *
 * <p>This is the SmallRye {@link ConfigMapping} bound to a task YAML file. The {@code up} and
 * {@code down} actions are plain SQL strings (the type parameter of {@link TaskDefinition} is
 * {@link String}). {@link JdbcMigrationNodeProvider} reads these values to build a {@link
 * JdbcMigrationNode}.
 *
 * <p>Example task YAML:
 *
 * <pre>{@code
 * name: create_database
 * target: admin
 * autocommit: true
 * up: "CREATE DATABASE myapp;"
 * down: "DROP DATABASE myapp;"
 * }</pre>
 */
@ConfigMapping(prefix = "")
public interface SqlTaskDefinition extends TaskDefinition<String> {

    /**
     * Returns the task name.
     *
     * @return the human readable task name
     */
    @Override
    String name();

    /**
     * Returns the optional task description.
     *
     * @return an {@link Optional} containing the description, or empty when none is configured
     */
    @Override
    Optional<String> description();

    /**
     * Returns the target identifier this task runs against.
     *
     * @return the target (environment) identifier
     */
    @Override
    String target();

    /**
     * Returns the optional list of dependency task identifiers.
     *
     * @return an {@link Optional} containing the dependency identifiers, or empty when the task has
     *     no declared dependencies
     */
    @Override
    Optional<List<String>> dependencies();

    /**
     * Returns the forward (UP) migration SQL.
     *
     * @return the SQL executed when the migration is applied
     */
    @Override
    String up();

    /**
     * Returns the optional rollback (DOWN) migration SQL.
     *
     * @return an {@link Optional} containing the rollback SQL, or empty when the task is not
     *     reversible
     */
    @Override
    Optional<String> down();

    /**
     * Returns whether the task runs in autocommit mode.
     *
     * <p>When {@code true}, statements are executed without an enclosing transaction. This is
     * required for SQL that cannot run inside a transaction, such as {@code CREATE DATABASE}.
     *
     * @return an {@link Optional} containing {@code true} to enable autocommit, or empty when
     *     unspecified (treated as {@code false})
     */
    Optional<Boolean> autocommit();

    /**
     * Returns why this migration cannot be rolled back, when the author declared that it cannot.
     *
     * <p>Written as {@code no_way_back: <reason>} in place of {@code down:}. It separates a
     * deliberate one-way migration from a rollback the author simply has not written — the two look
     * identical otherwise, and only one of them is something to leave alone. The reason is quoted
     * back when a rollback has to stop at this node.
     *
     * @return an {@link Optional} containing the reason, or empty when the migration is reversible
     */
    @WithName("no_way_back")
    Optional<String> noWayBack();
}
