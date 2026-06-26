package io.github.kakusuke.migraphe.cli.resolver;

/**
 * Thrown when a resolved artifact has no corresponding pin in the lockfile.
 *
 * <p>Raised during plugin resolution when a downloaded JAR (a plugin or one of its transitive
 * dependencies) is not recorded in {@code migraphe.lock.yaml}, so its integrity cannot be verified.
 * This typically means the lockfile is stale and {@code migraphe pin} must be re-run. It is a
 * {@link PluginResolutionException} and therefore unchecked.
 */
public class MissingChecksumPinException extends PluginResolutionException {

    /**
     * Creates the exception with a detail message.
     *
     * @param message the detail message naming the artifact that lacks a lockfile pin
     */
    public MissingChecksumPinException(String message) {
        super(message);
    }
}
