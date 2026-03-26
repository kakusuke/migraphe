package io.github.kakusuke.migraphe.jdbc;

/** JDBC プラグインで発生する例外。 */
public class JdbcException extends RuntimeException {

    public JdbcException(String message) {
        super(message);
    }

    public JdbcException(String message, Throwable cause) {
        super(message, cause);
    }
}
