package io.github.kakusuke.migraphe.cli.resolver;

import java.util.Map;
import java.util.Optional;

public record PluginDeclaration(
        MavenArtifactCoordinate coordinate, Optional<String> repositoryRef) {

    public static PluginDeclaration fromString(String coordinate) {
        return new PluginDeclaration(MavenArtifactCoordinate.parse(coordinate), Optional.empty());
    }

    public static PluginDeclaration fromMap(Map<?, ?> map) {
        String coordinate = requireString(map, "coordinate");
        Optional<String> repositoryRef = optionalString(map, "repository");
        return new PluginDeclaration(MavenArtifactCoordinate.parse(coordinate), repositoryRef);
    }

    private static String requireString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Plugin entry is missing required key '" + key + "'");
        }
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException(
                    "Plugin entry key '"
                            + key
                            + "' must be a string but was: "
                            + value.getClass().getSimpleName());
        }
        return s;
    }

    private static Optional<String> optionalString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException(
                    "Plugin entry key '"
                            + key
                            + "' must be a string but was: "
                            + value.getClass().getSimpleName());
        }
        return Optional.of(s);
    }
}
