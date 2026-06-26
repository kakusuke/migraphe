package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;

/**
 * {@link HistoryRepositoryProvider} that creates a {@link JdbcHistoryRepository} for a JDBC
 * environment.
 *
 * <p>Returned by {@link JdbcPlugin#historyRepositoryProvider()}. The migration history is persisted
 * in the same database described by the supplied {@link JdbcEnvironment}.
 */
public final class JdbcHistoryRepositoryProvider implements HistoryRepositoryProvider {

    /** Creates a new {@code JdbcHistoryRepositoryProvider}. */
    public JdbcHistoryRepositoryProvider() {}

    /**
     * Builds a {@link JdbcHistoryRepository} backed by the given environment.
     *
     * @param environment the environment whose database stores the history; must be a {@link
     *     JdbcEnvironment}
     * @return a new {@link JdbcHistoryRepository}
     * @throws JdbcException if {@code environment} is not a {@link JdbcEnvironment}
     */
    @Override
    public HistoryRepository createRepository(Environment environment) {
        if (!(environment instanceof JdbcEnvironment jdbcEnv)) {
            throw new JdbcException(
                    "Environment must be JdbcEnvironment, got: "
                            + environment.getClass().getName());
        }

        return new JdbcHistoryRepository(jdbcEnv);
    }
}
