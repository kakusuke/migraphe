package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.jdbc.JdbcEnvironment;
import io.github.kakusuke.migraphe.jdbc.statement.StatementSplitter;
import io.github.kakusuke.migraphe.postgresql.statement.PostgreSqlGrammar;
import org.jspecify.annotations.Nullable;

/** PostgreSQL 環境の実装。JdbcEnvironment を継承し、ドライバとラベルを固定する。 */
public final class PostgreSQLEnvironment extends JdbcEnvironment {

    private PostgreSQLEnvironment(
            EnvironmentId id,
            String name,
            String jdbcUrl,
            String username,
            @Nullable String password) {
        super(id, name, jdbcUrl, username, password, "org.postgresql.Driver", "PostgreSQL");
    }

    public static PostgreSQLEnvironment create(
            String name, String jdbcUrl, String username, @Nullable String password) {
        EnvironmentId id = EnvironmentId.of(name);
        return new PostgreSQLEnvironment(id, name, jdbcUrl, username, password);
    }

    @Override
    public StatementSplitter statementSplitter() {
        return PostgreSqlGrammar.splitter();
    }
}
