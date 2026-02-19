package io.github.kakusuke.migraphe.core.plugin.noop;

import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.EnvironmentProvider;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;

/**
 * noop プラグイン実装。
 *
 * <p>何もせず成功するタスクを実行する。外部DB不要で migraphe のグラフ構造を検証するために使用する。
 */
public final class NoopPlugin implements MigraphePlugin<String> {

    @Override
    public String type() {
        return "noop";
    }

    @Override
    public Class<? extends TaskDefinition<String>> taskDefinitionClass() {
        return NoopTaskDefinition.class;
    }

    @Override
    public Class<? extends EnvironmentDefinition> environmentDefinitionClass() {
        return NoopEnvironmentDefinition.class;
    }

    @Override
    public EnvironmentProvider environmentProvider() {
        return new NoopEnvironmentProvider();
    }

    @Override
    public MigrationNodeProvider<String> migrationNodeProvider() {
        return new NoopMigrationNodeProvider();
    }

    @Override
    public HistoryRepositoryProvider historyRepositoryProvider() {
        return new NoopHistoryRepositoryProvider();
    }
}
