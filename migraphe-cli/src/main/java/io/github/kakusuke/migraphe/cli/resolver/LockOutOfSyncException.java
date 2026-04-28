package io.github.kakusuke.migraphe.cli.resolver;

/** Thrown when {@code migraphe.lock.yaml} is out of sync with {@code migraphe.yaml}. */
public class LockOutOfSyncException extends PluginResolutionException {

    public LockOutOfSyncException(String message) {
        super(message);
    }
}
