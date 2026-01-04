package io.github.migraphe.postgresql;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.history.HistoryRepository;
import io.github.migraphe.api.spi.HistoryRepositoryProvider;

/** PostgreSQL HistoryRepository を生成する Provider。 */
public final class PostgreSQLHistoryRepositoryProvider implements HistoryRepositoryProvider {

    @Override
    public HistoryRepository createRepository(Environment environment) {
        if (!(environment instanceof PostgreSQLEnvironment)) {
            throw new PostgreSQLException(
                    "Environment must be PostgreSQLEnvironment, got: "
                            + environment.getClass().getName());
        }

        PostgreSQLEnvironment pgEnv = (PostgreSQLEnvironment) environment;
        return new PostgreSQLHistoryRepository(pgEnv);
    }
}
