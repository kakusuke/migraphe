package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.EnvironmentProvider;

/**
 * {@link EnvironmentProvider} that builds a generic {@link JdbcEnvironment} from configuration.
 *
 * <p>Returned by {@link JdbcPlugin#environmentProvider()} and invoked by the core configuration
 * layer once a target YAML has been mapped to a {@link JdbcEnvironmentDefinition}. It copies the
 * definition's attributes into a {@link JdbcEnvironment}, defaulting the database label to {@code
 * "JDBC"} when none is configured.
 */
public final class JdbcEnvironmentProvider implements EnvironmentProvider {

    /** Creates a new {@code JdbcEnvironmentProvider}. */
    public JdbcEnvironmentProvider() {}

    /**
     * Builds a {@link JdbcEnvironment} from the given definition.
     *
     * @param name the environment name (also used to derive the environment identifier)
     * @param definition the mapped environment configuration; must be a {@link
     *     JdbcEnvironmentDefinition}
     * @return a new {@link JdbcEnvironment}
     * @throws JdbcException if {@code definition} is not a {@link JdbcEnvironmentDefinition}
     */
    @Override
    public Environment createEnvironment(String name, EnvironmentDefinition definition) {
        if (!(definition instanceof JdbcEnvironmentDefinition jdbcDef)) {
            throw new JdbcException(
                    "Expected JdbcEnvironmentDefinition but got: "
                            + definition.getClass().getName());
        }

        return JdbcEnvironment.create(
                name,
                jdbcDef.jdbcUrl(),
                jdbcDef.username(),
                jdbcDef.password().orElse(null),
                jdbcDef.driverClass(),
                jdbcDef.dbLabel().orElse("JDBC"));
    }
}
