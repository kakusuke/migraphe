package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.jdbc.JdbcTarget;
import io.github.kakusuke.migraphe.jdbc.statement.StatementSplitter;
import io.github.kakusuke.migraphe.mysql.statement.MySqlGrammar;
import org.jspecify.annotations.Nullable;

/**
 * MySQL-specific {@link JdbcTarget}.
 *
 * <p>Extends the generic JDBC target, fixing the JDBC driver class to {@code
 * com.mysql.cj.jdbc.Driver} and the human-readable database label to {@code "MySQL"} so callers
 * only need to supply the connection coordinates. Instances are created by {@link
 * MySQLTargetProvider} from a {@link MySQLTargetDefinition} and used throughout the MySQL plugin to
 * open connections and to drive dialect-aware SQL statement splitting.
 *
 * @see JdbcTarget
 * @see MySQLTargetProvider
 * @see MySqlGrammar
 */
public final class MySQLTarget extends JdbcTarget {

    private MySQLTarget(
            TargetId id, String name, String jdbcUrl, String username, @Nullable String password) {
        super(id, name, jdbcUrl, username, password, "com.mysql.cj.jdbc.Driver", "MySQL");
    }

    /**
     * Creates a MySQL target from connection coordinates.
     *
     * <p>The {@link TargetId} is derived from {@code name} via {@link TargetId#of(String)}; the
     * driver class name and database label are fixed for MySQL and need not be supplied.
     *
     * @param name the environment name, also used to derive the {@link TargetId}
     * @param jdbcUrl the MySQL JDBC connection URL
     * @param username the database user name
     * @param password the database password, or {@code null} when no password is required
     * @return a new MySQL target configured with the given connection details
     */
    public static MySQLTarget create(
            String name, String jdbcUrl, String username, @Nullable String password) {
        TargetId id = TargetId.of(name);
        return new MySQLTarget(id, name, jdbcUrl, username, password);
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
