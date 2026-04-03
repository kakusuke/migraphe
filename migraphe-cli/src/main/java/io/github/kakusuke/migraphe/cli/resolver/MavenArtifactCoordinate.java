package io.github.kakusuke.migraphe.cli.resolver;

public record MavenArtifactCoordinate(String groupId, String artifactId, String version) {

    public static MavenArtifactCoordinate parse(String coordinate) {
        java.util.Objects.requireNonNull(coordinate, "coordinate must not be null");
        String[] parts = coordinate.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Invalid Maven coordinate (expected groupId:artifactId:version): "
                            + coordinate);
        }
        return new MavenArtifactCoordinate(parts[0], parts[1], parts[2]);
    }
}
