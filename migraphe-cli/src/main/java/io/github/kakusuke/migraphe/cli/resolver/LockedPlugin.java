package io.github.kakusuke.migraphe.cli.resolver;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A single top-level plugin pin within a {@link LockFile}.
 *
 * <p>Binds an exact Maven coordinate to the SHA-256 hash of its resolved JAR, alongside the pins of
 * its transitive dependencies. During plugin resolution each downloaded JAR is re-hashed and
 * checked against these pins to guarantee integrity.
 *
 * @param coordinate the exact Maven coordinate ({@code groupId:artifactId:version}) of the plugin;
 *     must not be {@code null}
 * @param sha256 the SHA-256 hash of the plugin JAR as 64 lowercase hexadecimal characters
 * @param dependencies the pins of the plugin's transitive dependencies; copied defensively into an
 *     immutable list
 */
public record LockedPlugin(
        MavenArtifactCoordinate coordinate, String sha256, List<LockedDependency> dependencies) {

    private static final Pattern HEX_64 = Pattern.compile("[0-9a-f]{64}");

    /**
     * Canonical constructor validating the hash format and copying the dependency list.
     *
     * @param coordinate the exact Maven coordinate of the plugin; must not be {@code null}
     * @param sha256 the SHA-256 hash; must be exactly 64 lowercase hexadecimal characters
     * @param dependencies the transitive dependency pins; defensively copied into an immutable list
     * @throws IllegalArgumentException if {@code coordinate} is {@code null}, or {@code sha256} is
     *     not 64 lowercase hex characters
     */
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
