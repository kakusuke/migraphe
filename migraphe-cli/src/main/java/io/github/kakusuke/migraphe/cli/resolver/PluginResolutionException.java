package io.github.kakusuke.migraphe.cli.resolver;

/**
 * Common parent for failures during plugin resolution and lockfile verification.
 *
 * <p>Subclasses signal specific failure modes — for example {@link LockFileNotFoundException},
 * {@link MissingChecksumPinException}, and {@link ChecksumMismatchException}. Catching this type
 * lets the CLI report any plugin-bootstrap failure uniformly. It is unchecked because these
 * failures abort startup rather than being recoverable in normal flow.
 */
public class PluginResolutionException extends RuntimeException {

    /**
     * Creates an exception with the given detail message.
     *
     * @param message the detail message describing the resolution failure
     */
    public PluginResolutionException(String message) {
        super(message);
    }
}
