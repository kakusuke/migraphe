package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.EnvironmentProvider;

/**
 * {@link EnvironmentProvider} that constructs {@link MySQLEnvironment} instances.
 *
 * <p>Returned by {@link MySQLPlugin#environmentProvider()}, this provider expects the runtime-bound
 * configuration to be a {@link MySQLEnvironmentDefinition} and translates its JDBC URL, user name,
 * and (optional) password into a concrete {@link MySQLEnvironment}.
 *
 * @see MySQLPlugin
 * @see MySQLEnvironment
 * @see MySQLEnvironmentDefinition
 */
public final class MySQLEnvironmentProvider implements EnvironmentProvider {

    /** Creates a new {@code MySQLEnvironmentProvider}. */
    public MySQLEnvironmentProvider() {}

    /**
     * Creates a {@link MySQLEnvironment} from a bound MySQL environment definition.
     *
     * @param name the environment name, which is the target ID from configuration
     * @param definition the bound environment configuration; must be a {@link
     *     MySQLEnvironmentDefinition}
     * @return the constructed {@link MySQLEnvironment}
     * @throws MySQLException if {@code definition} is not a {@link MySQLEnvironmentDefinition}
     */
    @Override
    public Environment createEnvironment(String name, EnvironmentDefinition definition) {
        if (!(definition instanceof MySQLEnvironmentDefinition mysqlDef)) {
            throw new MySQLException(
                    "Expected MySQLEnvironmentDefinition but got: "
                            + definition.getClass().getName());
        }

        return MySQLEnvironment.create(
                name, mysqlDef.jdbcUrl(), mysqlDef.username(), mysqlDef.password().orElse(null));
    }
}
