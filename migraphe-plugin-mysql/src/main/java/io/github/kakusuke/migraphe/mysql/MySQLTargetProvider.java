package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.api.spi.TargetDefinition;
import io.github.kakusuke.migraphe.api.spi.TargetProvider;
import io.github.kakusuke.migraphe.api.target.Target;

/**
 * {@link TargetProvider} that constructs {@link MySQLTarget} instances.
 *
 * <p>Returned by {@link MySQLPlugin#targetProvider()}, this provider expects the runtime-bound
 * configuration to be a {@link MySQLTargetDefinition} and translates its JDBC URL, user name, and
 * (optional) password into a concrete {@link MySQLTarget}.
 *
 * @see MySQLPlugin
 * @see MySQLTarget
 * @see MySQLTargetDefinition
 */
public final class MySQLTargetProvider implements TargetProvider {

    /** Creates a new {@code MySQLTargetProvider}. */
    public MySQLTargetProvider() {}

    /**
     * Creates a {@link MySQLTarget} from a bound MySQL target definition.
     *
     * @param name the environment name, which is the target ID from configuration
     * @param definition the bound target configuration; must be a {@link MySQLTargetDefinition}
     * @return the constructed {@link MySQLTarget}
     * @throws MySQLException if {@code definition} is not a {@link MySQLTargetDefinition}
     */
    @Override
    public Target createTarget(String name, TargetDefinition definition) {
        if (!(definition instanceof MySQLTargetDefinition mysqlDef)) {
            throw new MySQLException(
                    "Expected MySQLTargetDefinition but got: " + definition.getClass().getName());
        }

        return MySQLTarget.create(
                name, mysqlDef.jdbcUrl(), mysqlDef.username(), mysqlDef.password().orElse(null));
    }
}
