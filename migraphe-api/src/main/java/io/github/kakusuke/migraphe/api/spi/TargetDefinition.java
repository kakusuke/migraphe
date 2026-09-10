package io.github.kakusuke.migraphe.api.spi;

/**
 * Base interface for a plugin's target (target) configuration.
 *
 * <p>An target definition is the configuration view of a single target as declared in the project's
 * YAML. Each {@link MigraphePlugin} declares a concrete subtype via {@link
 * MigraphePlugin#targetDefinitionClass()} and implements it as a SmallRye {@code @ConfigMapping}
 * interface so its fields bind directly from YAML. The runtime then hands the bound definition to
 * {@link TargetProvider#createTarget(String, TargetDefinition)} to construct the concrete {@link
 * io.github.kakusuke.migraphe.api.target.Target}.
 *
 * <p>The only contract guaranteed by this base interface is {@link #type()}; plugin-specific
 * subtypes add whatever connection or configuration properties they require.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * @ConfigMapping(prefix = "")
 * public interface PostgreSQLTargetDefinition extends TargetDefinition {
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
 * @see MigraphePlugin#targetDefinitionClass()
 * @see TargetProvider
 */
public interface TargetDefinition {

    /**
     * Returns the target type identifier.
     *
     * <p>This is the type name used in configuration files (for example {@code "postgresql"},
     * {@code "mysql"}, or {@code "mongodb"}) and selects the plugin that handles this target.
     *
     * @return the target type identifier
     */
    String type();
}
