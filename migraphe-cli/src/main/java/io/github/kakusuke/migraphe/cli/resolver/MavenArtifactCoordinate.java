package io.github.kakusuke.migraphe.cli.resolver;

/**
 * A fully qualified Maven artifact coordinate identifying a single JAR.
 *
 * <p>Coordinates are the unit of identity used throughout plugin resolution and lockfile pinning:
 * they name the artifacts requested in the {@code plugins:} section of {@code migraphe.yaml}, the
 * artifacts resolved from Maven repositories, and the SHA-256 pins recorded in {@code
 * migraphe.lock.yaml}. The canonical text form is {@code groupId:artifactId:version}.
 *
 * @param groupId the Maven group identifier (for example {@code io.github.kakusuke})
 * @param artifactId the Maven artifact identifier (for example {@code migraphe-plugin-postgresql})
 * @param version the artifact version (for example {@code 0.4.2})
 */
public record MavenArtifactCoordinate(String groupId, String artifactId, String version) {

    /**
     * Parses a {@code groupId:artifactId:version} string into a coordinate.
     *
     * @param coordinate the colon-separated coordinate string; must contain exactly three
     *     colon-delimited segments
     * @return the parsed coordinate
     * @throws NullPointerException if {@code coordinate} is {@code null}
     * @throws IllegalArgumentException if {@code coordinate} does not have exactly three
     *     colon-separated segments
     */
    public static MavenArtifactCoordinate parse(String coordinate) {
        java.util.Objects.requireNonNull(coordinate, "coordinate must not be null");
        String[] parts = coordinate.split(":", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Invalid Maven coordinate (expected groupId:artifactId:version): "
                            + coordinate);
        }
        return new MavenArtifactCoordinate(parts[0], parts[1], parts[2]);
    }
}
