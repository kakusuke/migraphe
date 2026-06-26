package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.jdbc.JdbcEnvironment;
import io.github.kakusuke.migraphe.jdbc.statement.StatementSplitter;
import io.github.kakusuke.migraphe.postgresql.statement.PostgreSqlGrammar;
import org.jspecify.annotations.Nullable;

/**
 * PostgreSQL {@link io.github.kakusuke.migraphe.api.environment.Environment} implementation.
 *
 * <p>Extends the generic {@link JdbcEnvironment}, fixing the JDBC driver class name to {@code
 * org.postgresql.Driver} and the display label to {@code "PostgreSQL"}. It also overrides {@link
 * #statementSplitter()} to use the PostgreSQL dialect grammar, which understands dollar-quoted
 * bodies ({@code $tag$ ... $tag$}) in addition to standard string/comment regions.
 */
public final class PostgreSQLEnvironment extends JdbcEnvironment {

    private PostgreSQLEnvironment(
            EnvironmentId id,
            String name,
            String jdbcUrl,
            String username,
            @Nullable String password) {
        super(id, name, jdbcUrl, username, password, "org.postgresql.Driver", "PostgreSQL");
    }

    /**
     * Creates a PostgreSQL environment, deriving its {@link EnvironmentId} from {@code name}.
     *
     * @param name the environment/target name
     * @param jdbcUrl the JDBC connection URL, e.g. {@code jdbc:postgresql://host:5432/db}
     * @param username the database username
     * @param password the database password, or {@code null} if none is configured
     * @return a configured {@link PostgreSQLEnvironment}
     */
    public static PostgreSQLEnvironment create(
            String name, String jdbcUrl, String username, @Nullable String password) {
        EnvironmentId id = EnvironmentId.of(name);
        return new PostgreSQLEnvironment(id, name, jdbcUrl, username, password);
    }

    /**
     * Returns the PostgreSQL-dialect statement splitter.
     *
     * <p>Overrides the {@link JdbcEnvironment} default (standard quoting/comments only) with the
     * grammar from {@link PostgreSqlGrammar#splitter()}, which additionally treats dollar-quoted
     * regions as a single unit so embedded {@code ;} characters do not split a statement.
     *
     * @return the PostgreSQL {@link StatementSplitter}
     */
    @Override
    public StatementSplitter statementSplitter() {
        return PostgreSqlGrammar.splitter();
    }
}
