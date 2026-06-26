package io.github.kakusuke.migraphe.jdbc;

/**
 * Unchecked exception raised by the JDBC plugin family.
 *
 * <p>This exception wraps low-level failures (for example {@link java.sql.SQLException} or {@link
 * java.io.IOException}) and configuration mismatches that occur while creating environments,
 * building migration nodes, or persisting execution history through the generic JDBC plugin. It is
 * a {@link RuntimeException} because migration failures are not recoverable at the call site and
 * are surfaced through the CLI/Gradle presentation layers.
 *
 * <p>Database-specific plugins extend this type (for example {@code PostgreSQLException} and {@code
 * MySQLException}) so callers can catch the common JDBC supertype while still distinguishing
 * dialect-specific failures.
 */
public class JdbcException extends RuntimeException {

    /**
     * Creates an exception with the given detail message.
     *
     * @param message the detail message describing the failure
     */
    public JdbcException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given detail message and underlying cause.
     *
     * @param message the detail message describing the failure
     * @param cause the underlying cause (typically a {@link java.sql.SQLException} or {@link
     *     java.io.IOException})
     */
    public JdbcException(String message, Throwable cause) {
        super(message, cause);
    }
}
