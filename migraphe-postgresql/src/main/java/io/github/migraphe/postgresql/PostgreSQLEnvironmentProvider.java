package io.github.migraphe.postgresql;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.spi.EnvironmentDefinition;
import io.github.migraphe.api.spi.EnvironmentProvider;

/** PostgreSQL Environment を生成する Provider。 */
public final class PostgreSQLEnvironmentProvider implements EnvironmentProvider {

    @Override
    public Environment createEnvironment(String name, EnvironmentDefinition definition) {
        if (!(definition instanceof PostgreSQLEnvironmentDefinition pgDef)) {
            throw new PostgreSQLException(
                    "Expected PostgreSQLEnvironmentDefinition but got: "
                            + definition.getClass().getName());
        }

        return PostgreSQLEnvironment.create(
                name, pgDef.jdbcUrl(), pgDef.username(), pgDef.password());
    }
}
