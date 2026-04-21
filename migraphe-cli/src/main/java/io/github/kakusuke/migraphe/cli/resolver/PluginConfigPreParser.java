package io.github.kakusuke.migraphe.cli.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** migraphe.yaml から plugins セクションのみを事前パースする。 */
public final class PluginConfigPreParser {

    @SuppressWarnings("unchecked")
    public List<MavenArtifactCoordinate> parsePlugins(Path migrapheYaml) {
        if (!Files.exists(migrapheYaml)) {
            return Collections.emptyList();
        }
        try (InputStream in = Files.newInputStream(migrapheYaml)) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            if (root == null || !root.containsKey("plugins")) {
                return Collections.emptyList();
            }
            List<String> plugins = (List<String>) root.get("plugins");
            return plugins.stream().map(MavenArtifactCoordinate::parse).toList();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
