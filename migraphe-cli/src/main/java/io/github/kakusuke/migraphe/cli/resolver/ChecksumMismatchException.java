package io.github.kakusuke.migraphe.cli.resolver;

/**
 * Thrown when a resolved JAR's SHA-256 hash does not match its locked pin.
 *
 * <p>Raised during plugin resolution when a downloaded artifact is re-hashed and the result differs
 * from the {@code sha256} recorded in {@code migraphe.lock.yaml}, which indicates the artifact was
 * altered or replaced since it was pinned. It is a {@link PluginResolutionException} and therefore
 * unchecked.
 */
public class ChecksumMismatchException extends PluginResolutionException {

    /**
     * Creates the exception with a detail message.
     *
     * @param message the detail message naming the artifact and its expected versus actual hash
     */
    public ChecksumMismatchException(String message) {
        super(message);
    }
}
