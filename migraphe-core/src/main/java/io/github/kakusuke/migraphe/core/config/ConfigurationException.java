package io.github.kakusuke.migraphe.core.config;

/**
 * Unchecked exception signalling a configuration-file error.
 *
 * <p>Raised throughout the {@code config} package whenever loading or interpreting a Migraphe
 * configuration fails — for example a missing {@code migraphe.yaml}, an unreadable YAML file, a
 * malformed structure, or a target whose {@code type} cannot be resolved. Being a {@link
 * RuntimeException}, callers are not forced to declare it; the CLI and Gradle plugin layers catch
 * it to present a user-facing diagnostic.
 */
public class ConfigurationException extends RuntimeException {

    /**
     * Creates an exception with the given detail message.
     *
     * @param message the detail message describing the configuration error
     */
    public ConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given detail message and underlying cause.
     *
     * @param message the detail message describing the configuration error
     * @param cause the underlying cause (typically an {@link java.io.IOException})
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
