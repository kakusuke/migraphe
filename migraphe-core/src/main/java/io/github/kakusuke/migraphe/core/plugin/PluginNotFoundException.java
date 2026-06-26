package io.github.kakusuke.migraphe.core.plugin;

import java.util.Set;

/**
 * Unchecked exception thrown when no plugin is registered for a requested type.
 *
 * <p>Thrown by {@link PluginRegistry#getRequiredPlugin(String)}. Its detail message lists the
 * currently available plugin types and explains how to make the requested plugin available.
 */
public final class PluginNotFoundException extends RuntimeException {

    /** The plugin type that was requested but not found. */
    private final String requestedType;

    /** The plugin types that were registered when this exception was created. */
    private final Set<String> availableTypes;

    /**
     * Creates an exception for a missing plugin type.
     *
     * @param requestedType the plugin type that was requested but not found
     * @param availableTypes the plugin types currently registered; copied defensively
     */
    public PluginNotFoundException(String requestedType, Set<String> availableTypes) {
        super(buildMessage(requestedType, availableTypes));
        this.requestedType = requestedType;
        this.availableTypes = Set.copyOf(availableTypes);
    }

    private static String buildMessage(String requestedType, Set<String> availableTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append("No plugin found for type '").append(requestedType).append("'.");

        if (availableTypes.isEmpty()) {
            sb.append("\nNo plugins are currently loaded.");
        } else {
            sb.append("\nAvailable plugins: ").append(availableTypes);
        }

        sb.append("\n\nTo use this plugin type:");
        sb.append("\n  1. Place the plugin JAR file in ./plugins/ directory");
        sb.append(
                "\n"
                        + "  2. Ensure the JAR contains"
                        + " META-INF/services/io.github.kakusuke.migraphe.api.spi.MigraphePlugin");

        return sb.toString();
    }

    /**
     * Returns the plugin type that was requested but not found.
     *
     * @return the requested plugin type identifier
     */
    public String requestedType() {
        return requestedType;
    }

    /**
     * Returns the plugin types that were registered when this exception was created.
     *
     * @return an immutable set of the available plugin type identifiers
     */
    public Set<String> availableTypes() {
        return availableTypes;
    }
}
