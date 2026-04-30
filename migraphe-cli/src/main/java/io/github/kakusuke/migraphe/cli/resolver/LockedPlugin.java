package io.github.kakusuke.migraphe.cli.resolver;

import java.util.List;
import java.util.regex.Pattern;

public record LockedPlugin(
        MavenArtifactCoordinate coordinate, String sha256, List<LockedDependency> dependencies) {

    private static final Pattern HEX_64 = Pattern.compile("[0-9a-f]{64}");

    public LockedPlugin {
        if (coordinate == null) {
            throw new IllegalArgumentException("coordinate must not be null");
        }
        if (sha256 == null || sha256.length() != 64 || !HEX_64.matcher(sha256).matches()) {
            throw new IllegalArgumentException(
                    "sha256 must be 64 lowercase hex characters but was: " + sha256);
        }
        dependencies = List.copyOf(dependencies);
    }
}
