package io.github.kakusuke.migraphe.api.spi;

import io.github.kakusuke.migraphe.api.environment.Environment;

/**
 * Provider that constructs {@link Environment} instances from configuration.
 *
 * <p>This is one of the providers a {@link MigraphePlugin} exposes (via {@link
 * MigraphePlugin#environmentProvider()}). The runtime binds a target's YAML to the plugin's {@link
 * EnvironmentDefinition} subtype and then calls {@link #createEnvironment(String,
 * EnvironmentDefinition)} to turn that configuration into a usable {@link Environment}.
 *
 * <p>Implementors are responsible for interpreting the plugin-specific {@link
 * EnvironmentDefinition} (for example reading JDBC URL, username, and password) and producing the
 * concrete {@link Environment} their plugin operates on.
 *
 * @see MigraphePlugin#environmentProvider()
 * @see Environment
 * @see EnvironmentDefinition
 */
public interface EnvironmentProvider {

    /**
     * Creates an {@link Environment} from an environment definition.
     *
     * @param name the environment name, which is the target ID from configuration
     * @param definition the plugin-specific environment configuration to interpret
     * @return the constructed {@link Environment} instance
     */
    Environment createEnvironment(String name, EnvironmentDefinition definition);
}
