package io.github.kakusuke.migraphe.cli.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

/** migraphe.yaml から plugins / repositories セクションを事前パースする。 */
public final class PluginConfigPreParser {

    public PluginConfigParseResult parse(Path migrapheYaml) {
        if (!Files.exists(migrapheYaml)) {
            return new PluginConfigParseResult(List.of(), List.of());
        }
        try (InputStream in = Files.newInputStream(migrapheYaml)) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                return new PluginConfigParseResult(List.of(), List.of());
            }
            List<RepositoryConfig> repositories = parseRepositoryEntries(root.get("repositories"));
            List<PluginDeclaration> plugins = parsePluginEntries(root.get("plugins"));
            return new PluginConfigParseResult(repositories, plugins);
        } catch (IOException e) {
            return new PluginConfigParseResult(List.of(), List.of());
        }
    }

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
