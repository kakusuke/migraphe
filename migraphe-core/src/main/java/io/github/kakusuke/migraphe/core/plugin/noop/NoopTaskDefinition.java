package io.github.kakusuke.migraphe.core.plugin.noop;

import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.smallrye.config.ConfigMapping;
import java.util.List;
import java.util.Optional;

/**
 * {@link TaskDefinition} for the {@code "noop"} plugin.
 *
 * <p>The {@code up}/{@code down} values are descriptive text rather than SQL: the noop plugin only
 * reads them (to build human-readable task results) and never executes anything. It is a SmallRye
 * {@code @ConfigMapping} interface, so its properties bind directly from a task's YAML.
 *
 * <p>Example task YAML:
 *
 * <pre>{@code
 * name: Create users table
 * target: main
 * up: "Create the users table"
 * down: "Drop the users table"
 * }</pre>
 *
 * @see NoopPlugin
 */
@ConfigMapping(prefix = "")
public interface NoopTaskDefinition extends TaskDefinition<String> {

    @Override
    String name();

    @Override
    Optional<String> description();

    @Override
    String target();

    @Override
    Optional<List<String>> dependencies();

    /**
     * Returns the UP action as descriptive text.
     *
     * <p>For the noop plugin this is a human-readable description of the forward migration, not an
     * executable statement.
     *
     * @return the descriptive UP text
     */
    @Override
    String up();

    /**
     * Returns the optional DOWN action as descriptive text.
     *
     * <p>For the noop plugin this is a human-readable description of the rollback, not an
     * executable statement.
     *
     * @return an {@link Optional} containing the descriptive DOWN text, or an empty {@link
     *     Optional} if no rollback is configured
     */
    @Override
    Optional<String> down();
}
