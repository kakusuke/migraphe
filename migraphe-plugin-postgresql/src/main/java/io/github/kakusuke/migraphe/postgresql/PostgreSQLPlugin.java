package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TargetDefinition;
import io.github.kakusuke.migraphe.api.spi.TargetProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.jdbc.SqlTaskDefinition;

/**
 * PostgreSQL plugin implementation ({@code type="postgresql"}).
 *
 * <p>Bundles every piece needed to drive PostgreSQL migrations: the type discriminator, the
 * configuration definition classes, and the providers for targets, migration nodes, and the history
 * repository. Discovered at runtime via {@link java.util.ServiceLoader} (declared in {@code
 * META-INF/services/io.github.kakusuke.migraphe.api.spi.MigraphePlugin}) and registered into the
 * core {@code PluginRegistry}.
 *
 * <p>The UP/DOWN action type of a {@link TaskDefinition} is {@code String} (SQL text), so the
 * plugin is parameterized as {@code MigraphePlugin<String>}.
 */
public final class PostgreSQLPlugin implements MigraphePlugin<String> {

    /** Creates a new {@code PostgreSQLPlugin}. */
    public PostgreSQLPlugin() {}

    /**
     * Returns the plugin type discriminator.
     *
     * @return the string {@code "postgresql"}
     */
    @Override
    public String type() {
        return "postgresql";
    }

    /**
     * Returns the {@link TaskDefinition} subtype this plugin binds task YAML to.
     *
     * @return {@link SqlTaskDefinition}{@code .class}
     */
    @Override
    public Class<? extends TaskDefinition<String>> taskDefinitionClass() {
        return SqlTaskDefinition.class;
    }

    /**
     * Returns the {@link TargetDefinition} subtype this plugin binds target YAML to.
     *
     * @return {@link PostgreSQLTargetDefinition}{@code .class}
     */
    @Override
    public Class<? extends TargetDefinition> targetDefinitionClass() {
        return PostgreSQLTargetDefinition.class;
    }

    /**
     * Returns the provider that creates {@link PostgreSQLTarget} instances.
     *
     * @return a new {@link PostgreSQLTargetProvider}
     */
    @Override
    public TargetProvider targetProvider() {
        return new PostgreSQLTargetProvider();
    }

    /**
     * Returns the provider that creates PostgreSQL migration nodes.
     *
     * @return a new {@link PostgreSQLMigrationNodeProvider}
     */
    @Override
    public MigrationNodeProvider<String> migrationNodeProvider() {
        return new PostgreSQLMigrationNodeProvider();
    }

    /**
     * Returns the provider that creates the PostgreSQL history repository.
     *
     * @return a new {@link PostgreSQLHistoryRepositoryProvider}
     */
    @Override
    public HistoryRepositoryProvider historyRepositoryProvider() {
        return new PostgreSQLHistoryRepositoryProvider();
    }
}
