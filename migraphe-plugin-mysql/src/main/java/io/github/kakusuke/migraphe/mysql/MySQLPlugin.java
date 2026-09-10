package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TargetDefinition;
import io.github.kakusuke.migraphe.api.spi.TargetProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.jdbc.SqlTaskDefinition;

/**
 * {@link MigraphePlugin} implementation for MySQL targets.
 *
 * <p>Bundles, under the {@code "mysql"} type identifier, the configuration mapping types and
 * providers required to run migrations against MySQL: SQL-based tasks ({@link SqlTaskDefinition}),
 * {@link MySQLTargetDefinition} targets, and the MySQL-specific target, migration node, and history
 * repository providers. Tasks carry their UP/DOWN actions as SQL strings ({@code String}).
 *
 * <p>The plugin is discovered at runtime via {@link java.util.ServiceLoader}; it is registered in
 * the {@code META-INF/services/io.github.kakusuke.migraphe.api.spi.MigraphePlugin} resource and
 * selected when a configuration {@code type} equals {@code "mysql"}.
 *
 * @see MySQLTargetProvider
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
     * @return {@link MySQLTargetDefinition}, which maps MySQL target configuration from YAML
     */
    @Override
    public Class<? extends TargetDefinition> targetDefinitionClass() {
        return MySQLTargetDefinition.class;
    }

    /**
     * {@inheritDoc}
     *
     * @return a new {@link MySQLTargetProvider}
     */
    @Override
    public TargetProvider targetProvider() {
        return new MySQLTargetProvider();
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
