package io.github.kakusuke.migraphe.cli.resolver;

import java.util.regex.Pattern;

/**
 * A single transitive-dependency pin nested inside a {@link LockedPlugin}.
 *
 * <p>Binds an exact Maven coordinate to the SHA-256 hash of the resolved dependency JAR, so the
 * dependency can be integrity-checked during plugin resolution just like its owning plugin.
 *
 * @param coordinate the exact Maven coordinate ({@code groupId:artifactId:version}) of the
 *     dependency; must not be {@code null}
 * @param sha256 the SHA-256 hash of the dependency JAR as 64 lowercase hexadecimal characters
 */
public record LockedDependency(MavenArtifactCoordinate coordinate, String sha256) {

    private static final Pattern HEX_64 = Pattern.compile("[0-9a-f]{64}");

    /**
     * Canonical constructor validating the coordinate and hash format.
     *
     * @param coordinate the exact Maven coordinate of the dependency; must not be {@code null}
     * @param sha256 the SHA-256 hash; must be exactly 64 lowercase hexadecimal characters
     * @throws IllegalArgumentException if {@code coordinate} is {@code null}, or {@code sha256} is
     *     blank, not 64 characters long, or not lowercase hexadecimal
     */
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
