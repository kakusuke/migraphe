package io.github.kakusuke.migraphe.api.spi;

import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.target.Target;

/**
 * Provider that constructs {@link HistoryRepository} instances from an target.
 *
 * <p>This is one of the providers a {@link MigraphePlugin} exposes (via {@link
 * MigraphePlugin#historyRepositoryProvider()}). The runtime calls {@link #createRepository(Target)}
 * to obtain the store that records and queries migration execution history for a given target.
 *
 * <p>Implementors decide how and where history is persisted (for example in the target database, in
 * memory, or in a file) based on the supplied {@link Target}.
 *
 * @see MigraphePlugin#historyRepositoryProvider()
 * @see HistoryRepository
 */
public interface HistoryRepositoryProvider {

    /**
     * Creates a {@link HistoryRepository} for the given target.
     *
     * @param target the target whose migration history is to be stored and queried
     * @return the constructed {@link HistoryRepository} instance
     */
    HistoryRepository createRepository(Target target);
}
