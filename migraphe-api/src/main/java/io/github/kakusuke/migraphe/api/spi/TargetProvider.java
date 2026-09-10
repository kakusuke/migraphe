package io.github.kakusuke.migraphe.api.spi;

import io.github.kakusuke.migraphe.api.target.Target;

/**
 * Provider that constructs {@link Target} instances from configuration.
 *
 * <p>This is one of the providers a {@link MigraphePlugin} exposes (via {@link
 * MigraphePlugin#targetProvider()}). The runtime binds a target's YAML to the plugin's {@link
 * TargetDefinition} subtype and then calls {@link #createTarget(String, TargetDefinition)} to turn
 * that configuration into a usable {@link Target}.
 *
 * <p>Implementors are responsible for interpreting the plugin-specific {@link TargetDefinition}
 * (for example reading JDBC URL, username, and password) and producing the concrete {@link Target}
 * their plugin operates on.
 *
 * @see MigraphePlugin#targetProvider()
 * @see Target
 * @see TargetDefinition
 */
public interface TargetProvider {

    /**
     * Creates a {@link Target} from an target definition.
     *
     * @param name the environment name, which is the target ID from configuration
     * @param definition the plugin-specific target configuration to interpret
     * @return the constructed {@link Target} instance
     */
    Target createTarget(String name, TargetDefinition definition);
}
