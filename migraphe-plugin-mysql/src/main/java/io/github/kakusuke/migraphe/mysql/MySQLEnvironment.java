package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.jdbc.JdbcEnvironment;
import io.github.kakusuke.migraphe.jdbc.statement.StatementSplitter;
import io.github.kakusuke.migraphe.mysql.statement.MySqlGrammar;
import org.jspecify.annotations.Nullable;

/**
 * MySQL-specific {@link JdbcEnvironment}.
 *
 * <p>Extends the generic JDBC environment, fixing the JDBC driver class to {@code
 * com.mysql.cj.jdbc.Driver} and the human-readable database label to {@code "MySQL"} so callers
 * only need to supply the connection coordinates. Instances are created by {@link
 * MySQLEnvironmentProvider} from a {@link MySQLEnvironmentDefinition} and used throughout the MySQL
 * plugin to open connections and to drive dialect-aware SQL statement splitting.
 *
 * @see JdbcEnvironment
 * @see MySQLEnvironmentProvider
 * @see MySqlGrammar
 */
public final class MySQLEnvironment extends JdbcEnvironment {

    private MySQLEnvironment(
            EnvironmentId id,
            String name,
            String jdbcUrl,
            String username,
            @Nullable String password) {
        super(id, name, jdbcUrl, username, password, "com.mysql.cj.jdbc.Driver", "MySQL");
    }

    /**
     * Creates a MySQL environment from connection coordinates.
     *
     * <p>The {@link EnvironmentId} is derived from {@code name} via {@link
     * EnvironmentId#of(String)}; the driver class name and database label are fixed for MySQL and
     * need not be supplied.
     *
     * @param name the environment name, also used to derive the {@link EnvironmentId}
     * @param jdbcUrl the MySQL JDBC connection URL
     * @param username the database user name
     * @param password the database password, or {@code null} when no password is required
     * @return a new MySQL environment configured with the given connection details
     */
    public static MySQLEnvironment create(
            String name, String jdbcUrl, String username, @Nullable String password) {
        EnvironmentId id = EnvironmentId.of(name);
        return new MySQLEnvironment(id, name, jdbcUrl, username, password);
    }

    /**
     * Returns a {@link StatementSplitter} configured for the MySQL dialect.
     *
     * <p>The returned splitter understands backtick-quoted identifiers, {@code #} and {@code --}
     * line comments, recursive compound-statement blocks (such as {@code BEGIN...END}), and the
     * {@code DELIMITER} directive, so that semicolons inside those regions are not mistaken for
     * statement boundaries.
     *
     * @return the MySQL-dialect statement splitter produced by {@link MySqlGrammar#splitter()}
     */
    @Override
    public StatementSplitter statementSplitter() {
        return MySqlGrammar.splitter();
    }
}
