package io.github.kakusuke.migraphe.core.plugin;

/**
 * Unchecked exception thrown when a plugin cannot be loaded.
 *
 * <p>Raised by {@link PluginRegistry} when a discovered plugin declares a blank {@link
 * io.github.kakusuke.migraphe.api.spi.MigraphePlugin#type() type}, which leaves nothing for a
 * configuration {@code type:} to resolve to.
 */
public class PluginLoadException extends RuntimeException {

    /**
     * Creates an exception with the given detail message.
     *
     * @param message the detail message describing why loading failed
     */
    public PluginLoadException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given detail message and underlying cause.
     *
     * @param message the detail message describing why loading failed
     * @param cause the underlying cause of the failure
     */
    public PluginLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
