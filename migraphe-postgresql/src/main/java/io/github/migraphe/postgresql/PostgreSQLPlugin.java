package io.github.migraphe.postgresql;

import io.github.migraphe.api.spi.EnvironmentProvider;
import io.github.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.migraphe.api.spi.MigraphePlugin;
import io.github.migraphe.api.spi.MigrationNodeProvider;
import io.github.migraphe.api.spi.TaskDefinition;

/**
 * PostgreSQL プラグイン実装。
 *
 * <p>ServiceLoader で発見され、PluginRegistry に登録される。 TaskDefinition の UP/DOWN アクション型は String（SQL 文字列）。
 */
public final class PostgreSQLPlugin implements MigraphePlugin<String> {

    @Override
    public String type() {
        return "postgresql";
    }

    @Override
    public Class<? extends TaskDefinition<String>> taskDefinitionClass() {
        return SqlTaskDefinition.class;
    }

    @Override
    public EnvironmentProvider environmentProvider() {
        return new PostgreSQLEnvironmentProvider();
    }

    @Override
    public MigrationNodeProvider<String> migrationNodeProvider() {
        return new PostgreSQLMigrationNodeProvider();
    }

    @Override
    public HistoryRepositoryProvider historyRepositoryProvider() {
        return new PostgreSQLHistoryRepositoryProvider();
    }
}
