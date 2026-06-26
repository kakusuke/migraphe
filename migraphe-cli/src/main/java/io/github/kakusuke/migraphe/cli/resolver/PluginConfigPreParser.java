package io.github.kakusuke.migraphe.cli.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

/**
 * Pre-parses the {@code plugins:} and {@code repositories:} sections of {@code migraphe.yaml}.
 *
 * <p>Plugin classes live in JARs that are not on the classpath until they have been resolved, so
 * the full MicroProfile-based config loader cannot run first. This parser performs a deliberately
 * minimal, SnakeYAML-only read to learn which plugins and repositories are needed, producing a
 * {@link PluginConfigParseResult} that {@link PluginResolver} uses to build the plugin {@link
 * java.net.URLClassLoader URLClassLoader}. A missing or empty file yields an empty result;
 * structural errors within the recognized sections raise {@link IllegalArgumentException}.
 */
public final class PluginConfigPreParser {

    /** Creates a new {@code PluginConfigPreParser}. */
    public PluginConfigPreParser() {}

    /**
     * Pre-parses the given {@code migraphe.yaml}, extracting the repositories, plugins, and {@code
     * project.scan-root}.
     *
     * @param migrapheYaml the path to {@code migraphe.yaml}; need not exist
     * @return the extracted bootstrap configuration, or an all-empty result if the file is absent,
     *     empty, or unreadable
     * @throws IllegalArgumentException if the {@code repositories} or {@code plugins} section is
     *     present but malformed
     */
    public PluginConfigParseResult parse(Path migrapheYaml) {
        if (!Files.exists(migrapheYaml)) {
            return new PluginConfigParseResult(List.of(), List.of(), Optional.empty());
        }
        try (InputStream in = Files.newInputStream(migrapheYaml)) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                return new PluginConfigParseResult(List.of(), List.of(), Optional.empty());
            }
            List<RepositoryConfig> repositories = parseRepositoryEntries(root.get("repositories"));
            List<PluginDeclaration> plugins = parsePluginEntries(root.get("plugins"));
            Optional<String> scanRoot = parseScanRoot(root.get("project"));
            return new PluginConfigParseResult(repositories, plugins, scanRoot);
        } catch (IOException e) {
            return new PluginConfigParseResult(List.of(), List.of(), Optional.empty());
        }
    }

    private static Optional<String> parseScanRoot(@Nullable Object projectValue) {
        if (!(projectValue instanceof Map<?, ?> m)) {
            return Optional.empty();
        }
        Object v = m.get("scan-root");
        return v instanceof String s ? Optional.of(s) : Optional.empty();
    }

    /**
     * Convenience accessor returning only the plugin coordinates, discarding repository references.
     *
     * @param migrapheYaml the path to {@code migraphe.yaml}; need not exist
     * @return the declared plugin coordinates in declaration order, or an empty list if none
     * @throws IllegalArgumentException if the {@code plugins} section is present but malformed
     */
    public List<MavenArtifactCoordinate> parsePlugins(Path migrapheYaml) {
        return parse(migrapheYaml).plugins().stream().map(PluginDeclaration::coordinate).toList();
    }

    private static List<RepositoryConfig> parseRepositoryEntries(
            @Nullable Object repositoriesValue) {
        if (repositoriesValue == null) {
            return List.of();
        }
        if (!(repositoriesValue instanceof List<?> raw)) {
            throw new IllegalArgumentException(
                    "repositories must be a YAML list of {id, url} maps, got: %s value=%s"
                            .formatted(
                                    repositoriesValue.getClass().getSimpleName(),
                                    repositoriesValue));
        }
        List<RepositoryConfig> repositories = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Object element = raw.get(i);
            if (!(element instanceof Map<?, ?> m)) {
                throw new IllegalArgumentException(
                        "repositories[%d] must be a {id, url} map, got: %s value=%s"
                                .formatted(
                                        i,
                                        element == null ? "null" : element.getClass().getName(),
                                        element));
            }
            Object idValue = m.get("id");
            Object urlValue = m.get("url");
            if (!(idValue instanceof String id) || id.isBlank()) {
                throw new IllegalArgumentException(
                        "repositories[%d] is missing required string key 'id'".formatted(i));
            }
            if (!(urlValue instanceof String url) || url.isBlank()) {
                throw new IllegalArgumentException(
                        "repositories[%d] is missing required string key 'url'".formatted(i));
            }
            if (!url.startsWith("https://")) {
                throw new IllegalArgumentException(
                        "repositories[%d].url must start with 'https://' but was: %s"
                                .formatted(i, url));
            }
            try {
                repositories.add(new RepositoryConfig(id, url));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "repositories[%d]: %s".formatted(i, e.getMessage()), e);
            }
        }
        return Collections.unmodifiableList(repositories);
    }

    private static List<PluginDeclaration> parsePluginEntries(@Nullable Object pluginsValue) {
        if (pluginsValue == null) {
            return List.of();
        }
        if (!(pluginsValue instanceof List<?> raw)) {
            throw new IllegalArgumentException(
                    "plugins must be a YAML list of Maven coordinates, got: %s value=%s"
                            .formatted(pluginsValue.getClass().getSimpleName(), pluginsValue));
        }
        List<PluginDeclaration> plugins = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Object element = raw.get(i);
            if (element instanceof String s) {
                plugins.add(PluginDeclaration.fromString(s));
            } else if (element instanceof Map<?, ?> m) {
                try {
                    plugins.add(PluginDeclaration.fromMap(m));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "plugins[%d]: %s".formatted(i, e.getMessage()), e);
                }
            } else {
                throw new IllegalArgumentException(
                        "plugins[%d] must be a string Maven coordinate or a map, got: %s value=%s"
                                .formatted(
                                        i,
                                        element == null ? "null" : element.getClass().getName(),
                                        element));
            }
        }
        return Collections.unmodifiableList(plugins);
    }
}
