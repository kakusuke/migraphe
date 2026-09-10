package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.api.spi.TargetDefinition;
import io.github.kakusuke.migraphe.api.spi.TargetProvider;
import io.github.kakusuke.migraphe.api.target.Target;

/**
 * {@link TargetProvider} that builds {@link PostgreSQLTarget} instances.
 *
 * <p>Registered through {@link PostgreSQLPlugin}, this provider translates a {@link
 * PostgreSQLTargetDefinition} (loaded from a target YAML file) into a concrete {@link
 * PostgreSQLTarget}.
 */
public final class PostgreSQLTargetProvider implements TargetProvider {

    /** Creates a new {@code PostgreSQLTargetProvider}. */
    public PostgreSQLTargetProvider() {}

    /**
     * Creates a {@link PostgreSQLTarget} from the given definition.
     *
     * @param name the target/environment name (derived from the YAML file name)
     * @param definition the target definition; must be a {@link PostgreSQLTargetDefinition}
     * @return a configured {@link PostgreSQLTarget}
     * @throws PostgreSQLException if {@code definition} is not a {@link PostgreSQLTargetDefinition}
     */
    @Override
    public Target createTarget(String name, TargetDefinition definition) {
        if (!(definition instanceof PostgreSQLTargetDefinition pgDef)) {
            throw new PostgreSQLException(
                    "Expected PostgreSQLTargetDefinition but got: "
                            + definition.getClass().getName());
        }

        return PostgreSQLTarget.create(
                name, pgDef.jdbcUrl(), pgDef.username(), pgDef.password().orElse(null));
    }
}
