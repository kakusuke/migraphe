package io.github.kakusuke.migraphe.cli.resolver;

/**
 * Thrown when {@code migraphe.lock.yaml} is missing but plugins are declared in {@code
 * migraphe.yaml}.
 *
 * <p>Reproducible plugin resolution requires a committed lockfile; this exception signals that the
 * user must run {@code migraphe pin} to generate one. It is a {@link PluginResolutionException} and
 * therefore unchecked.
 */
public class LockFileNotFoundException extends PluginResolutionException {

    /**
     * Creates the exception with a detail message.
     *
     * @param message the detail message explaining which lockfile is missing and how to fix it
     */
    public LockFileNotFoundException(String message) {
        super(message);
    }
}
