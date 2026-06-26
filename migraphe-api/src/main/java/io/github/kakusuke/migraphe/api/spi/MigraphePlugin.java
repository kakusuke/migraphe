package io.github.kakusuke.migraphe.api.spi;

import io.github.kakusuke.migraphe.api.schema.SchemaInfoProvider;
import java.util.Optional;

/**
 * Central service-provider interface that bundles everything Migraphe needs to support one backend
 * type.
 *
 * <p>A {@code MigraphePlugin} ties together, under a single {@linkplain #type() type identifier},
 * the pieces required to run migrations against a particular kind of target (for example {@code
 * "postgresql"}, {@code "mysql"}, or {@code "jdbc"}): the configuration mapping types for tasks and
 * environments, and the providers that turn that configuration into runtime objects ({@link
 * EnvironmentProvider}, {@link MigrationNodeProvider}, and {@link HistoryRepositoryProvider}). It
 * may optionally expose a {@link SchemaInfoProvider} for the generator subsystem.
 *
 * <p>Implementations are discovered at runtime through the {@link java.util.ServiceLoader}
 * mechanism. To register a plugin, list its fully qualified class name in a {@code
 * META-INF/services/io.github.kakusuke.migraphe.api.spi.MigraphePlugin} resource file on the
 * classpath. The runtime selects an implementation by matching a configuration {@code type} value
 * against {@link #type()}, then uses the returned definition classes to bind YAML configuration and
 * the returned providers to construct runtime objects.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class PostgreSQLPlugin implements MigraphePlugin<String> {
 *     @Override
 *     public String type() {
 *         return "postgresql";
 *     }
 *
 *     @Override
 *     public Class<SqlTaskDefinition> taskDefinitionClass() {
 *         return SqlTaskDefinition.class;
 *     }
 *
 *     @Override
 *     public Class<PostgreSQLEnvironmentDefinition> environmentDefinitionClass() {
 *         return PostgreSQLEnvironmentDefinition.class;
 *     }
 *
 *     @Override
 *     public EnvironmentProvider environmentProvider() {
 *         return new PostgreSQLEnvironmentProvider();
 *     }
 *
 *     @Override
 *     public MigrationNodeProvider<String> migrationNodeProvider() {
 *         return new PostgreSQLMigrationNodeProvider();
 *     }
 *
 *     @Override
 *     public HistoryRepositoryProvider historyRepositoryProvider() {
 *         return new PostgreSQLHistoryRepositoryProvider();
 *     }
 * }
 * }</pre>
 *
 * @param <T> the type of the UP/DOWN action carried by this plugin's {@link TaskDefinition} (for
 *     example {@code String} for SQL-based plugins such as PostgreSQL)
 * @see EnvironmentProvider
 * @see MigrationNodeProvider
 * @see HistoryRepositoryProvider
 * @see TaskDefinition
 * @see EnvironmentDefinition
 */
public interface MigraphePlugin<T> {

    /**
     * Returns this plugin's type identifier.
     *
     * <p>This is the type name used in configuration files (for example {@code "postgresql"},
     * {@code "mysql"}, or {@code "mongodb"}) and is how the runtime selects this plugin. It must be
     * unique among the plugins on the classpath.
     *
     * @return the plugin's type identifier
     */
    String type();

    /**
     * Returns the plugin-specific {@link TaskDefinition} subtype.
     *
     * <p>The framework uses the returned class to bind task configuration from YAML. The subtype is
     * expected to be implemented as a SmallRye {@code @ConfigMapping} interface.
     *
     * @return the {@link Class} of this plugin's {@link TaskDefinition} subtype
     */
    Class<? extends TaskDefinition<T>> taskDefinitionClass();

    /**
     * Returns the plugin-specific {@link EnvironmentDefinition} subtype.
     *
     * <p>The framework uses the returned class to bind environment configuration from YAML. The
     * subtype is expected to be implemented as a SmallRye {@code @ConfigMapping} interface.
     *
     * @return the {@link Class} of this plugin's {@link EnvironmentDefinition} subtype
     */
    Class<? extends EnvironmentDefinition> environmentDefinitionClass();

    /**
     * Returns the provider that constructs {@link
     * io.github.kakusuke.migraphe.api.environment.Environment} instances for this plugin.
     *
     * @return the {@link EnvironmentProvider} for this plugin
     */
    EnvironmentProvider environmentProvider();

    /**
     * Returns the provider that constructs {@link
     * io.github.kakusuke.migraphe.api.graph.MigrationNode} instances for this plugin.
     *
     * @return the {@link MigrationNodeProvider} for this plugin
     */
    MigrationNodeProvider<T> migrationNodeProvider();

    /**
     * Returns the provider that constructs {@link
     * io.github.kakusuke.migraphe.api.history.HistoryRepository} instances for this plugin.
     *
     * @return the {@link HistoryRepositoryProvider} for this plugin
     */
    HistoryRepositoryProvider historyRepositoryProvider();

    /**
     * Returns this plugin's {@link SchemaInfoProvider}, if any.
     *
     * <p>A schema-info provider lets the generator subsystem extract schema information from an
     * environment. Plugins that do not support schema extraction can rely on the default, which
     * returns {@link Optional#empty()}.
     *
     * @return an {@link Optional} containing the plugin's {@link SchemaInfoProvider}, or an empty
     *     {@link Optional} if the plugin does not provide one
     */
    default Optional<SchemaInfoProvider<?>> schemaInfoProvider() {
        return Optional.empty();
    }
}
