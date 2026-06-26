package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.EnvironmentProvider;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.jdbc.SqlTaskDefinition;

/**
 * {@link MigraphePlugin} implementation for MySQL targets.
 *
 * <p>Bundles, under the {@code "mysql"} type identifier, the configuration mapping types and
 * providers required to run migrations against MySQL: SQL-based tasks ({@link SqlTaskDefinition}),
 * {@link MySQLEnvironmentDefinition} environments, and the MySQL-specific environment, migration
 * node, and history repository providers. Tasks carry their UP/DOWN actions as SQL strings ({@code
 * String}).
 *
 * <p>The plugin is discovered at runtime via {@link java.util.ServiceLoader}; it is registered in
 * the {@code META-INF/services/io.github.kakusuke.migraphe.api.spi.MigraphePlugin} resource and
 * selected when a configuration {@code type} equals {@code "mysql"}.
 *
 * @see MySQLEnvironmentProvider
 * @see MySQLMigrationNodeProvider
 * @see MySQLHistoryRepositoryProvider
 */
public final class MySQLPlugin implements MigraphePlugin<String> {

    /** Creates a new {@code MySQLPlugin}. */
    public MySQLPlugin() {}

    /**
     * {@inheritDoc}
     *
     * @return the literal {@code "mysql"}
     */
    @Override
    public String type() {
        return "mysql";
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link SqlTaskDefinition}, which maps SQL UP/DOWN actions from YAML
     */
    @Override
    public Class<? extends TaskDefinition<String>> taskDefinitionClass() {
        return SqlTaskDefinition.class;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link MySQLEnvironmentDefinition}, which maps MySQL target configuration from YAML
     */
    @Override
    public Class<? extends EnvironmentDefinition> environmentDefinitionClass() {
        return MySQLEnvironmentDefinition.class;
    }

    /**
     * {@inheritDoc}
     *
     * @return a new {@link MySQLEnvironmentProvider}
     */
    @Override
    public EnvironmentProvider environmentProvider() {
        return new MySQLEnvironmentProvider();
    }

    /**
     * {@inheritDoc}
     *
     * @return a new {@link MySQLMigrationNodeProvider}
     */
    @Override
    public MigrationNodeProvider<String> migrationNodeProvider() {
        return new MySQLMigrationNodeProvider();
    }

    /**
     * {@inheritDoc}
     *
     * @return a new {@link MySQLHistoryRepositoryProvider}
     */
    @Override
    public HistoryRepositoryProvider historyRepositoryProvider() {
        return new MySQLHistoryRepositoryProvider();
    }
}
