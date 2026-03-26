package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.EnvironmentProvider;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.jdbc.SqlTaskDefinition;

/**
 * MySQL プラグイン実装。
 *
 * <p>ServiceLoader で発見され、PluginRegistry に登録される。 TaskDefinition の UP/DOWN アクション型は String（SQL 文字列）。
 */
public final class MySQLPlugin implements MigraphePlugin<String> {

    @Override
    public String type() {
        return "mysql";
    }

    @Override
    public Class<? extends TaskDefinition<String>> taskDefinitionClass() {
        return SqlTaskDefinition.class;
    }

    @Override
    public Class<? extends EnvironmentDefinition> environmentDefinitionClass() {
        return MySQLEnvironmentDefinition.class;
    }

    @Override
    public EnvironmentProvider environmentProvider() {
        return new MySQLEnvironmentProvider();
    }

    @Override
    public MigrationNodeProvider<String> migrationNodeProvider() {
        return new MySQLMigrationNodeProvider();
    }

    @Override
    public HistoryRepositoryProvider historyRepositoryProvider() {
        return new MySQLHistoryRepositoryProvider();
    }
}
