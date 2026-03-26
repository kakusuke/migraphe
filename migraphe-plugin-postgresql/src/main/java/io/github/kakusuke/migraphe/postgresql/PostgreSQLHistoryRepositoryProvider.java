package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.jdbc.JdbcHistoryRepository;

/** PostgreSQL HistoryRepository を生成する Provider。 */
public final class PostgreSQLHistoryRepositoryProvider implements HistoryRepositoryProvider {

    private static final String PG_SCHEMA_RESOURCE =
            "/io/github/kakusuke/migraphe/postgresql/schema/init_history_table.sql";

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
