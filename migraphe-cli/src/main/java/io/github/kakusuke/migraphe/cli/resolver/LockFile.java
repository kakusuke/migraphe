package io.github.kakusuke.migraphe.cli.resolver;

import java.util.List;

/**
 * In-memory representation of the {@code migraphe.lock.yaml} lockfile.
 *
 * <p>The lockfile pins every resolved plugin (and its transitive dependencies) to an exact Maven
 * coordinate plus a SHA-256 hash of the downloaded JAR, so that subsequent plugin resolution is
 * reproducible and tamper-evident. It is produced by {@code migraphe pin} (via {@link
 * LockFileBuilder} and {@link LockFileWriter}), read back by {@link LockFileReader}, and verified
 * against {@code migraphe.yaml} by {@link LockSyncChecker}.
 *
 * @param version the lockfile schema version; must equal {@link #CURRENT_VERSION}
 * @param plugins the locked top-level plugins, each with its dependency pins; copied defensively
 *     into an immutable list
 */
public record LockFile(int version, List<LockedPlugin> plugins) {

    /** The lockfile schema version this build understands and emits. */
    public static final int CURRENT_VERSION = 1;

    /**
     * Canonical constructor enforcing the supported schema version and copying the plugin list.
     *
     * @param version the lockfile schema version; must equal {@link #CURRENT_VERSION}
     * @param plugins the locked top-level plugins; defensively copied into an immutable list
     * @throws IllegalArgumentException if {@code version} is not {@link #CURRENT_VERSION}
     */
    public LockFile {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported lockfile-version: "
                            + version
                            + " (expected "
                            + CURRENT_VERSION
                            + ")");
        }
        plugins = List.copyOf(plugins);
    }
}
