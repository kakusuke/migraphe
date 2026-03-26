package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.EnvironmentProvider;

/** MySQL Environment を生成する Provider。 */
public final class MySQLEnvironmentProvider implements EnvironmentProvider {

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
