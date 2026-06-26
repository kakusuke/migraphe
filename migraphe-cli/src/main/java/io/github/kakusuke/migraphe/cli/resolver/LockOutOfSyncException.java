package io.github.kakusuke.migraphe.cli.resolver;

/**
 * Thrown when {@code migraphe.lock.yaml} is out of sync with {@code migraphe.yaml}.
 *
 * <p>Raised by {@link LockSyncChecker} when a plugin has been added, removed, or had its version
 * changed in the configuration without the lockfile being regenerated. It is a {@link
 * PluginResolutionException} and therefore unchecked; the detail message enumerates the specific
 * drift and prompts the user to run {@code migraphe pin}.
 */
public class LockOutOfSyncException extends PluginResolutionException {

    /**
     * Creates the exception with a detail message.
     *
     * @param message the detail message listing the detected drift between config and lockfile
     */
    public LockOutOfSyncException(String message) {
        super(message);
    }
}
