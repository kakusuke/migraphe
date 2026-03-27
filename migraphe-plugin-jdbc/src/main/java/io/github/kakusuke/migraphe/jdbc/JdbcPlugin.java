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
 * 汎用 JDBC プラグイン実装。
 *
 * <p>ServiceLoader で発見され、PluginRegistry に登録される。 任意の JDBC データベースに対して使用可能。
 */
public final class JdbcPlugin implements MigraphePlugin<String> {

    @Override
    public String type() {
        return "jdbc";
    }

    @Override
    public Class<? extends TaskDefinition<String>> taskDefinitionClass() {
        return SqlTaskDefinition.class;
    }

    @Override
    public Class<? extends EnvironmentDefinition> environmentDefinitionClass() {
        return JdbcEnvironmentDefinition.class;
    }

    @Override
    public EnvironmentProvider environmentProvider() {
        return new JdbcEnvironmentProvider();
    }

    @Override
    public MigrationNodeProvider<String> migrationNodeProvider() {
        return new JdbcMigrationNodeProvider();
    }

    @Override
    public HistoryRepositoryProvider historyRepositoryProvider() {
        return new JdbcHistoryRepositoryProvider();
    }

    @Override
    public Optional<SchemaInfoProvider<?>> schemaInfoProvider() {
        return Optional.of(new JdbcSchemaInfoProvider());
    }
}
