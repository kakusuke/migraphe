package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;

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
