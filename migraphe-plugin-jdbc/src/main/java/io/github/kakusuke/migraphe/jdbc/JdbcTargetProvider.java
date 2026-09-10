package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.spi.TargetDefinition;
import io.github.kakusuke.migraphe.api.spi.TargetProvider;
import io.github.kakusuke.migraphe.api.target.Target;

/**
 * {@link TargetProvider} that builds a generic {@link JdbcTarget} from configuration.
 *
 * <p>Returned by {@link JdbcPlugin#targetProvider()} and invoked by the core configuration layer
 * once a target YAML has been mapped to a {@link JdbcTargetDefinition}. It copies the definition's
 * attributes into a {@link JdbcTarget}, defaulting the database label to {@code "JDBC"} when none
 * is configured.
 */
public final class JdbcTargetProvider implements TargetProvider {

    /** Creates a new {@code JdbcTargetProvider}. */
    public JdbcTargetProvider() {}

    /**
     * Builds a {@link JdbcTarget} from the given definition.
     *
     * @param name the environment name (also used to derive the target identifier)
     * @param definition the mapped target configuration; must be a {@link JdbcTargetDefinition}
     * @return a new {@link JdbcTarget}
     * @throws JdbcException if {@code definition} is not a {@link JdbcTargetDefinition}
     */
    @Override
    public Target createTarget(String name, TargetDefinition definition) {
        if (!(definition instanceof JdbcTargetDefinition jdbcDef)) {
            throw new JdbcException(
                    "Expected JdbcTargetDefinition but got: " + definition.getClass().getName());
        }

        return JdbcTarget.create(
                name,
                jdbcDef.jdbcUrl(),
                jdbcDef.username(),
                jdbcDef.password().orElse(null),
                jdbcDef.driverClass(),
                jdbcDef.dbLabel().orElse("JDBC"));
    }
}
