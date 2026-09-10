package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.jdbc.JdbcHistoryRepository;

/**
 * {@link HistoryRepositoryProvider} that builds the PostgreSQL execution-history store.
 *
 * <p>Delegates to the generic {@link JdbcHistoryRepository}, supplying a PostgreSQL-specific schema
 * initialization script ({@code init_history_table.sql}) used to create the history table.
 * Registered through {@link PostgreSQLPlugin}.
 */
public final class PostgreSQLHistoryRepositoryProvider implements HistoryRepositoryProvider {

    /** Creates a new {@code PostgreSQLHistoryRepositoryProvider}. */
    public PostgreSQLHistoryRepositoryProvider() {}

    /** Classpath location of the PostgreSQL history-table DDL script. */
    private static final String PG_SCHEMA_RESOURCE =
            "/io/github/kakusuke/migraphe/postgresql/schema/init_history_table.sql";

    /**
     * Creates a {@link JdbcHistoryRepository} bound to the given PostgreSQL target.
     *
     * @param target the target to persist history into; must be a {@link PostgreSQLTarget}
     * @return a {@link HistoryRepository} backed by the PostgreSQL connection and DDL script
     * @throws PostgreSQLException if {@code target} is not a {@link PostgreSQLTarget}
     */
    @Override
    public HistoryRepository createRepository(Target target) {
        if (!(target instanceof PostgreSQLTarget pgEnv)) {
            throw new PostgreSQLException(
                    "Target must be PostgreSQLTarget, got: " + target.getClass().getName());
        }

        return new JdbcHistoryRepository(pgEnv, PG_SCHEMA_RESOURCE);
    }
}
