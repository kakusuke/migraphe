package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.schema.SchemaInfoProvider;
import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.EnvironmentProvider;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfoProvider;
import java.util.Optional;

/**
 * Generic JDBC plugin implementation and the reference implementation of {@link MigraphePlugin}.
 *
 * <p>This plugin is discovered at runtime by {@link java.util.ServiceLoader} via the {@code
 * META-INF/services/io.github.kakusuke.migraphe.api.spi.MigraphePlugin} resource and registered in
 * the core plugin registry under the type {@code "jdbc"}. It can be used against any JDBC database
 * by configuring the driver class and connection details in YAML, and it serves as the base for the
 * PostgreSQL and MySQL plugins.
 *
 * <p>The plugin bundles the full set of providers required by the core orchestration layer: it maps
 * {@link SqlTaskDefinition} and {@link JdbcEnvironmentDefinition} from YAML, builds environments,
 * migration nodes, and history repositories, and exposes a JDBC schema-info provider for
 * generators.
 */
public final class JdbcPlugin implements MigraphePlugin<String> {

    /** Creates a new {@code JdbcPlugin}. */
    public JdbcPlugin() {}

    /**
     * Returns the plugin type discriminator, {@code "jdbc"}.
     *
     * @return the plugin type identifier matched against {@code type:} in YAML
     */
    @Override
    public String type() {
        return "jdbc";
    }

    /**
     * Returns the task-definition mapping type used to bind task YAML.
     *
     * @return {@link SqlTaskDefinition}{@code .class}
     */
    @Override
    public Class<? extends TaskDefinition<String>> taskDefinitionClass() {
        return SqlTaskDefinition.class;
    }

    /**
     * Returns the environment-definition mapping type used to bind target YAML.
     *
     * @return {@link JdbcEnvironmentDefinition}{@code .class}
     */
    @Override
    public Class<? extends EnvironmentDefinition> environmentDefinitionClass() {
        return JdbcEnvironmentDefinition.class;
    }

    /**
     * Returns the provider that builds {@link JdbcEnvironment} instances.
     *
     * @return a new {@link JdbcEnvironmentProvider}
     */
    @Override
    public EnvironmentProvider environmentProvider() {
        return new JdbcEnvironmentProvider();
    }

    /**
     * Returns the provider that builds {@link JdbcMigrationNode} instances.
     *
     * @return a new {@link JdbcMigrationNodeProvider}
     */
    @Override
    public MigrationNodeProvider<String> migrationNodeProvider() {
        return new JdbcMigrationNodeProvider();
    }

    /**
     * Returns the provider that builds {@link JdbcHistoryRepository} instances.
     *
     * @return a new {@link JdbcHistoryRepositoryProvider}
     */
    @Override
    public HistoryRepositoryProvider historyRepositoryProvider() {
        return new JdbcHistoryRepositoryProvider();
    }

    /**
     * Returns the schema-info provider used by schema/markdown generators.
     *
     * @return an {@link Optional} containing a new {@link JdbcSchemaInfoProvider}
     */
    @Override
    public Optional<SchemaInfoProvider<?>> schemaInfoProvider() {
        return Optional.of(new JdbcSchemaInfoProvider());
    }
}
