package io.github.kakusuke.migraphe.cli.resolver;

import java.util.Map;
import java.util.Optional;

/**
 * A single entry from the {@code plugins:} section of {@code migraphe.yaml}.
 *
 * <p>Each entry in that section may be written either as a bare {@code groupId:artifactId:version}
 * string or as a map with a {@code coordinate} key and an optional {@code repository} key. Both
 * forms are normalized into this record by {@link PluginConfigPreParser}. The optional {@code
 * repositoryRef} names a {@link RepositoryConfig} (by its {@code id}) that {@link
 * MavenPluginResolver} should query exclusively for this plugin; when absent, the plugin is
 * resolved against every configured repository.
 *
 * @param coordinate the Maven coordinate of the plugin artifact to resolve
 * @param repositoryRef the {@code id} of the repository to restrict resolution to, or empty to
 *     query all configured repositories
 */
public record PluginDeclaration(
        MavenArtifactCoordinate coordinate, Optional<String> repositoryRef) {

    /**
     * Creates a declaration from a bare {@code groupId:artifactId:version} coordinate string with
     * no repository restriction.
     *
     * @param coordinate the colon-separated Maven coordinate string
     * @return a declaration whose {@code repositoryRef} is empty
     * @throws IllegalArgumentException if {@code coordinate} is not a valid Maven coordinate
     */
    public static PluginDeclaration fromString(String coordinate) {
        return new PluginDeclaration(MavenArtifactCoordinate.parse(coordinate), Optional.empty());
    }

    /**
     * Creates a declaration from a YAML map entry containing a required {@code coordinate} key and
     * an optional {@code repository} key.
     *
     * @param map the parsed YAML map for a single plugin entry
     * @return a declaration carrying the parsed coordinate and optional repository reference
     * @throws IllegalArgumentException if the {@code coordinate} key is missing, if either value is
     *     present but not a string, or if the coordinate is malformed
     */
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
