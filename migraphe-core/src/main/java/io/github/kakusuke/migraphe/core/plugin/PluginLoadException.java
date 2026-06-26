package io.github.kakusuke.migraphe.core.plugin;

/**
 * Unchecked exception thrown when a plugin cannot be loaded.
 *
 * <p>Raised by {@link PluginRegistry} when a JAR or plugins directory cannot be read, contains no
 * plugins, or refers to an invalid path, and when an attempt is made to register a plugin with a
 * blank {@link io.github.kakusuke.migraphe.api.spi.MigraphePlugin#type() type}.
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
