package io.github.migraphe.core.plugin;

/** プラグイン読み込み時の例外。 */
public class PluginLoadException extends RuntimeException {

    public PluginLoadException(String message) {
        super(message);
    }

    public PluginLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
