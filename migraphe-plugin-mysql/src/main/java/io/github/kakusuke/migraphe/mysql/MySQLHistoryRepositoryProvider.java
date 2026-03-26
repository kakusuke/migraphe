package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.jdbc.JdbcHistoryRepository;

/** MySQL HistoryRepository を生成する Provider。 */
public final class MySQLHistoryRepositoryProvider implements HistoryRepositoryProvider {

    private static final String MYSQL_SCHEMA_RESOURCE =
            "/io/github/kakusuke/migraphe/mysql/schema/init_history_table.sql";

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
