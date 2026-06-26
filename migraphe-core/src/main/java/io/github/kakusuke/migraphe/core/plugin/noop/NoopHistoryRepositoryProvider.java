package io.github.kakusuke.migraphe.core.plugin.noop;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;

/**
 * {@link HistoryRepositoryProvider} for the {@code "noop"} plugin.
 *
 * <p>Returns an {@link InMemoryHistoryRepository}, so execution history is kept only for the
 * lifetime of the process and never persisted. This matches the noop plugin's goal of validating a
 * project without any external storage.
 *
 * @see NoopPlugin
 */
public final class NoopHistoryRepositoryProvider implements HistoryRepositoryProvider {

    /** Creates a new {@code NoopHistoryRepositoryProvider}. */
    public NoopHistoryRepositoryProvider() {}

    /**
     * Creates an in-memory history repository for the given environment.
     *
     * @param environment the environment the repository is associated with; not used, as the
     *     in-memory store needs no connection
     * @return a new {@link InMemoryHistoryRepository}
     */
    @Override
    public HistoryRepository createRepository(Environment environment) {
        return new InMemoryHistoryRepository();
    }
}
