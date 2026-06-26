package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.jdbc.JdbcHistoryRepository;

/**
 * {@link HistoryRepositoryProvider} that constructs MySQL-backed history repositories.
 *
 * <p>Returned by {@link MySQLPlugin#historyRepositoryProvider()}, this provider creates a {@link
 * JdbcHistoryRepository} that persists execution history in the MySQL target itself, using a
 * MySQL-specific DDL script to initialize the history table.
 *
 * @see MySQLPlugin
 * @see JdbcHistoryRepository
 */
public final class MySQLHistoryRepositoryProvider implements HistoryRepositoryProvider {

    /** Creates a new {@code MySQLHistoryRepositoryProvider}. */
    public MySQLHistoryRepositoryProvider() {}

    /** Classpath location of the MySQL DDL script that creates the history table. */
    private static final String MYSQL_SCHEMA_RESOURCE =
            "/io/github/kakusuke/migraphe/mysql/schema/init_history_table.sql";

    /**
     * Creates a {@link JdbcHistoryRepository} for the given MySQL environment.
     *
     * @param environment the environment whose migration history is to be stored; must be a {@link
     *     MySQLEnvironment}
     * @return a {@link JdbcHistoryRepository} backed by the MySQL target
     * @throws MySQLException if {@code environment} is not a {@link MySQLEnvironment}
     */
    @Override
    public HistoryRepository createRepository(Environment environment) {
        if (!(environment instanceof MySQLEnvironment mysqlEnv)) {
            throw new MySQLException(
                    "Environment must be MySQLEnvironment, got: "
                            + environment.getClass().getName());
        }

        return new JdbcHistoryRepository(mysqlEnv, MYSQL_SCHEMA_RESOURCE);
    }
}
