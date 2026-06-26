package io.github.kakusuke.migraphe.cli.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

/**
 * Reads a {@link LockFile} from a {@code migraphe.lock.yaml} file.
 *
 * <p>Parses the YAML produced by {@link LockFileWriter} back into the typed model, validating the
 * structure as it goes: the {@code lockfile-version} key must be a present integer, {@code plugins}
 * must be a list of maps, and each plugin map must carry a {@code coordinate}, a {@code sha256},
 * and an optional {@code dependencies} list. Structural problems surface as {@link
 * IllegalArgumentException} with a message identifying the offending entry.
 */
public final class LockFileReader {

    /** Creates a new {@code LockFileReader}. */
    public LockFileReader() {}

    /**
     * Reads and parses the lockfile at the given path.
     *
     * @param lockFilePath the path to the {@code migraphe.lock.yaml} file
     * @return the parsed {@link LockFile}, or {@link Optional#empty()} if the file does not exist
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if the file content is malformed (missing or non-integer
     *     {@code lockfile-version}, non-list {@code plugins}, or a plugin/dependency entry with a
     *     missing or wrong-typed required key)
     */
    public Optional<LockFile> read(Path lockFilePath) throws IOException {
        if (!Files.exists(lockFilePath)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(lockFilePath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                throw new IllegalArgumentException("lockfile-version is required");
            }
            int version = readVersion(root.get("lockfile-version"));
            List<LockedPlugin> plugins = readPlugins(root.get("plugins"));
            return Optional.of(new LockFile(version, plugins));
        }
    }

    private static int readVersion(@Nullable Object value) {
        if (value == null) {
            throw new IllegalArgumentException("lockfile-version is required");
        }
        if (!(value instanceof Integer i)) {
            throw new IllegalArgumentException(
                    "lockfile-version must be an integer but was: " + value);
        }
        return i;
    }

    private static List<LockedPlugin> readPlugins(@Nullable Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> raw)) {
            throw new IllegalArgumentException(
                    "plugins must be a list but was: " + value.getClass().getSimpleName());
        }
        List<LockedPlugin> plugins = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Object element = raw.get(i);
            if (!(element instanceof Map<?, ?> m)) {
                throw new IllegalArgumentException(
                        "plugins[%d] must be a map but was: %s"
                                .formatted(
                                        i,
                                        element == null ? "null" : element.getClass().getName()));
            }
            try {
                plugins.add(readPlugin(m));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "plugins[%d]: %s".formatted(i, e.getMessage()), e);
            }
        }
        return plugins;
    }

    private static LockedPlugin readPlugin(Map<?, ?> map) {
        MavenArtifactCoordinate coordinate =
                MavenArtifactCoordinate.parse(requireString(map, "coordinate"));
        String sha256 = requireString(map, "sha256");
        List<LockedDependency> deps = readDependencies(map.get("dependencies"));
        return new LockedPlugin(coordinate, sha256, deps);
    }

    private static List<LockedDependency> readDependencies(@Nullable Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> raw)) {
            throw new IllegalArgumentException(
                    "dependencies must be a list but was: " + value.getClass().getSimpleName());
        }
        List<LockedDependency> deps = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Object element = raw.get(i);
            if (!(element instanceof Map<?, ?> m)) {
                throw new IllegalArgumentException("dependencies[%d] must be a map".formatted(i));
            }
            MavenArtifactCoordinate coord =
                    MavenArtifactCoordinate.parse(requireString(m, "coordinate"));
            String sha256 = requireString(m, "sha256");
            deps.add(new LockedDependency(coord, sha256));
        }
        return deps;
    }

    private static String requireString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required key '" + key + "'");
        }
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException(
                    "Key '" + key + "' must be a string but was: " + value);
        }
        return s;
    }
}
