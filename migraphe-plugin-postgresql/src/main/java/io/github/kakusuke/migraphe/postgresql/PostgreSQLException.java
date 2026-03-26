package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.jdbc.JdbcException;

/** PostgreSQL プラグインで発生する例外。 */
public class PostgreSQLException extends JdbcException {

    public PostgreSQLException(String message) {
        super(message);
    }

    public PostgreSQLException(String message, Throwable cause) {
        super(message, cause);
    }
}
