package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.jdbc.JdbcException;

/**
 * Unchecked exception raised by the MySQL plugin.
 *
 * <p>Extends {@link JdbcException} so callers can catch the common JDBC supertype while still
 * distinguishing MySQL-specific failures, such as configuration mismatches (a non-MySQL environment
 * or task definition) or low-level errors surfaced while operating against a MySQL target.
 *
 * @see JdbcException
 */
public class MySQLException extends JdbcException {

    /**
     * Creates an exception with the given detail message.
     *
     * @param message the detail message describing the failure
     */
    public MySQLException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given detail message and underlying cause.
     *
     * @param message the detail message describing the failure
     * @param cause the underlying cause of the failure
     */
    public MySQLException(String message, Throwable cause) {
        super(message, cause);
    }
}
