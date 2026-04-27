package io.github.kakusuke.migraphe.cli.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** migraphe.yaml から plugins セクションのみを事前パースする。 */
public final class PluginConfigPreParser {

    public List<MavenArtifactCoordinate> parsePlugins(Path migrapheYaml) {
        if (!Files.exists(migrapheYaml)) {
            return Collections.emptyList();
        }
        try (InputStream in = Files.newInputStream(migrapheYaml)) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            if (root == null || root.get("plugins") == null) {
                return Collections.emptyList();
            }
            List<?> raw = (List<?>) root.get("plugins");
            List<String> plugins = new ArrayList<>();
            for (int i = 0; i < raw.size(); i++) {
                Object element = raw.get(i);
                if (element instanceof String s) {
                    plugins.add(s);
                } else {
                    throw new IllegalArgumentException(
                            "plugins["
                                    + i
                                    + "] must be a string Maven coordinate, got: "
                                    + (element == null ? "null" : element.getClass().getName()));
                }
            }
            return plugins.stream().map(MavenArtifactCoordinate::parse).toList();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
