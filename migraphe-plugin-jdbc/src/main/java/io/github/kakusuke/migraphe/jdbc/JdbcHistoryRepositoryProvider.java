package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.api.target.Target;

/**
 * {@link HistoryRepositoryProvider} that creates a {@link JdbcHistoryRepository} for a JDBC target.
 *
 * <p>Returned by {@link JdbcPlugin#historyRepositoryProvider()}. The migration history is persisted
 * in the same database described by the supplied {@link JdbcTarget}.
 */
public final class JdbcHistoryRepositoryProvider implements HistoryRepositoryProvider {

    /** Creates a new {@code JdbcHistoryRepositoryProvider}. */
    public JdbcHistoryRepositoryProvider() {}

    /**
     * Builds a {@link JdbcHistoryRepository} backed by the given target.
     *
     * @param target the target whose database stores the history; must be a {@link JdbcTarget}
     * @return a new {@link JdbcHistoryRepository}
     * @throws JdbcException if {@code target} is not a {@link JdbcTarget}
     */
    @Override
    public HistoryRepository createRepository(Target target) {
        if (!(target instanceof JdbcTarget jdbcEnv)) {
            throw new JdbcException(
                    "Target must be JdbcTarget, got: " + target.getClass().getName());
        }

        return new JdbcHistoryRepository(jdbcEnv);
    }
}
