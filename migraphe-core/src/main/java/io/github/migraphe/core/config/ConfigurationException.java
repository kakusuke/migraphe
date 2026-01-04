package io.github.migraphe.core.config;

/** 設定ファイルエラー（パースエラー、ファイル不在、無効な構造など）を表す例外。 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
