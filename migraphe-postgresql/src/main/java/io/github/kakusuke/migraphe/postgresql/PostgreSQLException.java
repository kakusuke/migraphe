package io.github.kakusuke.migraphe.postgresql;

/** PostgreSQL プラグインで発生する例外。 */
public class PostgreSQLException extends RuntimeException {

    public PostgreSQLException(String message) {
        super(message);
    }

    public PostgreSQLException(String message, Throwable cause) {
        super(message, cause);
    }
}
