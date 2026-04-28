package io.github.kakusuke.migraphe.cli.resolver;

/** Common parent for failures during plugin resolution and lockfile verification. */
public class PluginResolutionException extends RuntimeException {

    public PluginResolutionException(String message) {
        super(message);
    }
}
