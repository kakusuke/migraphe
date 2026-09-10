package io.github.kakusuke.migraphe.core.plugin.noop;

import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TargetDefinition;
import io.github.kakusuke.migraphe.api.spi.TargetProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;

/**
 * Built-in {@code "noop"} plugin implementation.
 *
 * <p>The noop plugin executes tasks that do nothing and always succeed, and persists history in
 * memory. It requires no external database, which makes it ideal for validating a Migraphe
 * project's graph structure, dependency ordering, and configuration without touching any real
 * target.
 *
 * <p>Its UP/DOWN action type is {@link String}: task {@code up}/{@code down} values are treated as
 * descriptive text rather than executable SQL. The pieces it binds together are {@link
 * NoopTaskDefinition} and {@link NoopTargetDefinition} for configuration, and {@link
 * NoopTargetProvider}, {@link NoopMigrationNodeProvider}, and {@link NoopHistoryRepositoryProvider}
 * for runtime objects.
 *
 * <p>This plugin is registered for {@link java.util.ServiceLoader} discovery through a {@code
 * META-INF/services/io.github.kakusuke.migraphe.api.spi.MigraphePlugin} resource.
 *
 * @see MigraphePlugin
 */
public final class NoopPlugin implements MigraphePlugin<String> {

    /** Creates a new {@code NoopPlugin}. */
    public NoopPlugin() {}

    @Override
    public String type() {
        return "noop";
    }

    @Override
    public Class<? extends TaskDefinition<String>> taskDefinitionClass() {
        return NoopTaskDefinition.class;
    }

    @Override
    public Class<? extends TargetDefinition> targetDefinitionClass() {
        return NoopTargetDefinition.class;
    }

    @Override
    public TargetProvider targetProvider() {
        return new NoopTargetProvider();
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
