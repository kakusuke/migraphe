package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
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
     * Creates a {@link JdbcHistoryRepository} bound to the given PostgreSQL environment.
     *
     * @param environment the environment to persist history into; must be a {@link
     *     PostgreSQLEnvironment}
     * @return a {@link HistoryRepository} backed by the PostgreSQL connection and DDL script
     * @throws PostgreSQLException if {@code environment} is not a {@link PostgreSQLEnvironment}
     */
    @Override
    public HistoryRepository createRepository(Environment environment) {
        if (!(environment instanceof PostgreSQLEnvironment pgEnv)) {
            throw new PostgreSQLException(
                    "Environment must be PostgreSQLEnvironment, got: "
                            + environment.getClass().getName());
        }

        return new JdbcHistoryRepository(pgEnv, PG_SCHEMA_RESOURCE);
    }
}
