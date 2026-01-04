package io.github.migraphe.postgresql;

import io.github.migraphe.api.spi.EnvironmentProvider;
import io.github.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.migraphe.api.spi.MigraphePlugin;
import io.github.migraphe.api.spi.MigrationNodeProvider;

/**
 * PostgreSQL プラグイン実装。
 *
 * <p>ServiceLoader で発見され、PluginRegistry に登録される。
 */
public final class PostgreSQLPlugin implements MigraphePlugin {

    @Override
    public String type() {
        return "postgresql";
    }

    @Override
    public EnvironmentProvider environmentProvider() {
        return new PostgreSQLEnvironmentProvider();
    }

    @Override
    public MigrationNodeProvider migrationNodeProvider() {
        return new PostgreSQLMigrationNodeProvider();
    }

    @Override
    public HistoryRepositoryProvider historyRepositoryProvider() {
        return new PostgreSQLHistoryRepositoryProvider();
    }
}
