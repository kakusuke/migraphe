package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.EnvironmentProvider;

/** 汎用 JDBC Environment を生成する Provider。 */
public final class JdbcEnvironmentProvider implements EnvironmentProvider {

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
