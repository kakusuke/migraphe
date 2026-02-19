package io.github.kakusuke.migraphe.core.plugin.noop;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;

/** noop HistoryRepository を生成する Provider。InMemoryHistoryRepository を返す。 */
public final class NoopHistoryRepositoryProvider implements HistoryRepositoryProvider {

    @Override
    public HistoryRepository createRepository(Environment environment) {
        return new InMemoryHistoryRepository();
    }
}
