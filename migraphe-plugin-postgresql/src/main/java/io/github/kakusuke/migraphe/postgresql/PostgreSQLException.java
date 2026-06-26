package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.jdbc.JdbcException;

/**
 * Runtime exception raised by the PostgreSQL plugin.
 *
 * <p>Extends {@link JdbcException} so that callers handling generic JDBC-plugin failures also catch
 * PostgreSQL-specific ones. It is thrown for PostgreSQL plugin misconfiguration and type
 * mismatches, such as receiving a non-{@link PostgreSQLEnvironment} {@link
 * io.github.kakusuke.migraphe.api.environment.Environment} or an unexpected {@link
 * io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition}/{@link
 * io.github.kakusuke.migraphe.api.spi.TaskDefinition} subtype.
 */
public class PostgreSQLException extends JdbcException {

    /**
     * Creates an exception with the given detail message.
     *
     * @param message the detail message describing the failure
     */
    public PostgreSQLException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given detail message and underlying cause.
     *
     * @param message the detail message describing the failure
     * @param cause the underlying cause of this exception
     */
    public PostgreSQLException(String message, Throwable cause) {
        super(message, cause);
    }
}
