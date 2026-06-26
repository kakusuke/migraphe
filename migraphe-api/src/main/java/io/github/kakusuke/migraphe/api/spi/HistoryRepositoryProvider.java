package io.github.kakusuke.migraphe.api.spi;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;

/**
 * Provider that constructs {@link HistoryRepository} instances from an environment.
 *
 * <p>This is one of the providers a {@link MigraphePlugin} exposes (via {@link
 * MigraphePlugin#historyRepositoryProvider()}). The runtime calls {@link
 * #createRepository(Environment)} to obtain the store that records and queries migration execution
 * history for a given target.
 *
 * <p>Implementors decide how and where history is persisted (for example in the target database, in
 * memory, or in a file) based on the supplied {@link Environment}.
 *
 * @see MigraphePlugin#historyRepositoryProvider()
 * @see HistoryRepository
 */
public interface HistoryRepositoryProvider {

    /**
     * Creates a {@link HistoryRepository} for the given environment.
     *
     * @param environment the environment whose migration history is to be stored and queried
     * @return the constructed {@link HistoryRepository} instance
     */
    HistoryRepository createRepository(Environment environment);
}
