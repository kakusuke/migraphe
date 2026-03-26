package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;

/** 汎用 JDBC HistoryRepository を生成する Provider。 */
public final class JdbcHistoryRepositoryProvider implements HistoryRepositoryProvider {

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
