package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.jdbc.JdbcException;

/** MySQL プラグインで発生する例外。 */
public class MySQLException extends JdbcException {

    public MySQLException(String message) {
        super(message);
    }

    public MySQLException(String message, Throwable cause) {
        super(message, cause);
    }
}
