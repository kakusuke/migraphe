package io.github.kakusuke.migraphe.api.spi;

/**
 * Base interface for a plugin's environment (target) configuration.
 *
 * <p>An environment definition is the configuration view of a single target as declared in the
 * project's YAML. Each {@link MigraphePlugin} declares a concrete subtype via {@link
 * MigraphePlugin#environmentDefinitionClass()} and implements it as a SmallRye
 * {@code @ConfigMapping} interface so its fields bind directly from YAML. The runtime then hands
 * the bound definition to {@link EnvironmentProvider#createEnvironment(String,
 * EnvironmentDefinition)} to construct the concrete {@link
 * io.github.kakusuke.migraphe.api.environment.Environment}.
 *
 * <p>The only contract guaranteed by this base interface is {@link #type()}; plugin-specific
 * subtypes add whatever connection or configuration properties they require.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * @ConfigMapping(prefix = "")
 * public interface PostgreSQLEnvironmentDefinition extends EnvironmentDefinition {
 *     @Override
 *     String type();
 *
 *     @WithName("jdbc_url")
 *     String jdbcUrl();
 *
 *     String username();
 *
 *     String password();
 * }
 * }</pre>
 *
 * @see MigraphePlugin#environmentDefinitionClass()
 * @see EnvironmentProvider
 */
public interface EnvironmentDefinition {

    /**
     * Returns the environment type identifier.
     *
     * <p>This is the type name used in configuration files (for example {@code "postgresql"},
     * {@code "mysql"}, or {@code "mongodb"}) and selects the plugin that handles this target.
     *
     * @return the environment type identifier
     */
    String type();
}
