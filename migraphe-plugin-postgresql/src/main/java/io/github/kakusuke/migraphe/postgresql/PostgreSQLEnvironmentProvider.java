package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.EnvironmentProvider;

/**
 * {@link EnvironmentProvider} that builds {@link PostgreSQLEnvironment} instances.
 *
 * <p>Registered through {@link PostgreSQLPlugin}, this provider translates a {@link
 * PostgreSQLEnvironmentDefinition} (loaded from a target YAML file) into a concrete {@link
 * PostgreSQLEnvironment}.
 */
public final class PostgreSQLEnvironmentProvider implements EnvironmentProvider {

    /** Creates a new {@code PostgreSQLEnvironmentProvider}. */
    public PostgreSQLEnvironmentProvider() {}

    /**
     * Creates a {@link PostgreSQLEnvironment} from the given definition.
     *
     * @param name the target/environment name (derived from the YAML file name)
     * @param definition the environment definition; must be a {@link
     *     PostgreSQLEnvironmentDefinition}
     * @return a configured {@link PostgreSQLEnvironment}
     * @throws PostgreSQLException if {@code definition} is not a {@link
     *     PostgreSQLEnvironmentDefinition}
     */
    @Override
    public Environment createEnvironment(String name, EnvironmentDefinition definition) {
        if (!(definition instanceof PostgreSQLEnvironmentDefinition pgDef)) {
            throw new PostgreSQLException(
                    "Expected PostgreSQLEnvironmentDefinition but got: "
                            + definition.getClass().getName());
        }

        return PostgreSQLEnvironment.create(
                name, pgDef.jdbcUrl(), pgDef.username(), pgDef.password().orElse(null));
    }
}
