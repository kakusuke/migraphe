package io.github.kakusuke.migraphe.cli.resolver;

/** Thrown when {@code migraphe.lock.yaml} is missing but plugins are declared. */
public class LockFileNotFoundException extends PluginResolutionException {

    public LockFileNotFoundException(String message) {
        super(message);
    }
}
