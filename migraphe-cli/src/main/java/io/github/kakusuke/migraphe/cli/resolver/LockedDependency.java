package io.github.kakusuke.migraphe.cli.resolver;

import java.util.regex.Pattern;

public record LockedDependency(MavenArtifactCoordinate coordinate, String sha256) {

    private static final Pattern HEX_64 = Pattern.compile("[0-9a-f]{64}");

    public LockedDependency {
        if (coordinate == null) {
            throw new IllegalArgumentException("coordinate must not be null");
        }
        if (sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException("sha256 must not be blank");
        }
        if (sha256.length() != 64) {
            throw new IllegalArgumentException(
                    "sha256 must be 64 hex characters but was: " + sha256.length());
        }
        if (!HEX_64.matcher(sha256).matches()) {
            throw new IllegalArgumentException("sha256 must be lowercase hex but was: " + sha256);
        }
    }
}
