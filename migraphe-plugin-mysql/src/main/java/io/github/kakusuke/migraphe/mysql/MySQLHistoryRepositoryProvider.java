package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.api.target.Target;
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

    private static final String MYSQL_UPGRADE_RESOURCE =
            "/io/github/kakusuke/migraphe/mysql/schema/upgrade_history_table.sql";

    /**
     * Creates a {@link JdbcHistoryRepository} for the given MySQL target.
     *
     * @param target the target whose migration history is to be stored; must be a {@link
     *     MySQLTarget}
     * @return a {@link JdbcHistoryRepository} backed by the MySQL target
     * @throws MySQLException if {@code target} is not a {@link MySQLTarget}
     */
    @Override
    public HistoryRepository createRepository(Target target) {
        if (!(target instanceof MySQLTarget mysqlEnv)) {
            throw new MySQLException(
                    "Target must be MySQLTarget, got: " + target.getClass().getName());
        }

        return new JdbcHistoryRepository(mysqlEnv, MYSQL_SCHEMA_RESOURCE, MYSQL_UPGRADE_RESOURCE);
    }
}
