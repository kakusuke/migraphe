package io.github.kakusuke.migraphe.cli.resolver;

import java.util.List;

public record LockFile(int version, List<LockedPlugin> plugins) {

    public static final int CURRENT_VERSION = 1;

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
