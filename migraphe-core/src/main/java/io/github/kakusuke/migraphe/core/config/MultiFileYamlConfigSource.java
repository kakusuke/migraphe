package io.github.kakusuke.migraphe.core.config;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.smallrye.config.source.yaml.YamlConfigSource;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * A {@link ConfigSource} that merges Migraphe's several YAML files into a single flat key space.
 *
 * <p>Each source file is loaded with {@link YamlConfigSource} and its keys are re-namespaced
 * according to the file's role, so that the combined config can be addressed by a single set of
 * property names. The merge happens eagerly in the constructor; the resulting map is exposed at
 * ordinal {@code 100}, the lowest tier in the layering, so that environment files, system
 * properties, OS environment variables and explicit variables can all override it.
 *
 * <p>Prefixing strategy:
 *
 * <ul>
 *   <li>{@code migraphe.yaml} &rarr; no prefix ({@code project.*}, {@code history.*})
 *   <li>{@code targets/db1.yaml} &rarr; {@code "target.db1.*"} prefix
 *   <li>{@code tasks/db1/create_users.yaml} &rarr; {@code "task.\"db1/create_users\".*"} prefix
 *       (the task id is quoted so embedded slashes are not treated as key separators)
 * </ul>
 */
public class MultiFileYamlConfigSource implements ConfigSource {

    private static final String NAME = "MultiFileYamlConfigSource";
    private static final int ORDINAL = 100;

    private final Map<String, String> properties = new HashMap<>();

    /**
     * Loads and merges the given YAML files into a single config source.
     *
     * @param projectConfigFile the path to {@code migraphe.yaml} (loaded without a prefix)
     * @param targetFiles the {@code targets/*.yaml} files (each prefixed by its target id)
     * @param taskFiles the task files keyed by their {@link NodeId} (each prefixed by its task id)
     * @throws ConfigurationException if any YAML file cannot be read
     */
    public MultiFileYamlConfigSource(
            Path projectConfigFile, List<Path> targetFiles, Map<NodeId, Path> taskFiles) {
        loadProjectConfig(projectConfigFile);
        loadTargetConfigs(targetFiles);
        loadTaskConfigs(taskFiles);
    }

    /**
     * Loads {@code migraphe.yaml} with no prefix applied.
     *
     * @param file the path to {@code migraphe.yaml}
     * @throws ConfigurationException if the YAML file cannot be read
     */
    private void loadProjectConfig(Path file) {
        try {
            YamlConfigSource yamlSource = new YamlConfigSource(file.toUri().toURL());
            properties.putAll(yamlSource.getProperties());
        } catch (IOException e) {
            throw new ConfigurationException("Failed to load project config: " + file, e);
        }
    }

    /**
     * Loads each {@code targets/*.yaml} file under a {@code "target.<targetId>.*"} prefix, where
     * the target id is derived from the file name.
     *
     * @param files the {@code targets/*.yaml} files
     * @throws ConfigurationException if any YAML file cannot be read
     */
    private void loadTargetConfigs(List<Path> files) {
        for (Path file : files) {
            try {
                // Derive the targetId from the file name (db1.yaml -> "db1").
                String targetId = file.getFileName().toString().replaceAll("\\.yaml$", "");

                // Load the YAML.
                YamlConfigSource yamlSource = rawAwareYamlSource(file);

                // Merge in with the prefix applied.
                for (Map.Entry<String, String> entry : yamlSource.getProperties().entrySet()) {
                    String prefixedKey = "target." + targetId + "." + entry.getKey();
                    properties.put(prefixedKey, entry.getValue());
                }
            } catch (IOException e) {
                throw new ConfigurationException("Failed to load target config: " + file, e);
            }
        }
    }

    /**
     * Loads each task file under a {@code "task.\"<taskId>\".*"} prefix. The task id is quoted so
     * that ids containing slashes (such as {@code db1/create_users}) are not split into nested
     * keys.
     *
     * @param taskFiles the task files keyed by their {@link NodeId}
     * @throws ConfigurationException if any YAML file cannot be read
     */
    private void loadTaskConfigs(Map<NodeId, Path> taskFiles) {
        for (Map.Entry<NodeId, Path> entry : taskFiles.entrySet()) {
            NodeId taskId = entry.getKey();
            Path file = entry.getValue();

            try {
                // Load the YAML.
                YamlConfigSource yamlSource = rawAwareYamlSource(file);

                // Merge in with the prefix applied.
                // When a task id contains slashes, the form is "task.\"db1/create_users\".*".
                for (Map.Entry<String, String> prop : yamlSource.getProperties().entrySet()) {
                    String prefixedKey = "task.\"" + taskId.value() + "\"." + prop.getKey();
                    properties.put(prefixedKey, prop.getValue());
                }
            } catch (IOException e) {
                throw new ConfigurationException("Failed to load task config: " + file, e);
            }
        }
    }

    /**
     * Loads a YAML file, first rewriting every {@code !raw} scalar so that expansion yields it
     * verbatim.
     *
     * <p>{@link YamlConfigSource} rejects an unknown tag, so the file is composed into nodes, the
     * tagged scalars are replaced, and the result is serialized back for {@link YamlConfigSource}
     * to flatten as usual.
     *
     * @param file the YAML file
     * @param ordinal the ordinal of the returned source
     * @return the loaded source
     * @throws IOException if the file cannot be read
     */
    static YamlConfigSource rawAwareYamlSource(Path file, int ordinal) throws IOException {
        Yaml yaml = new Yaml();
        StringWriter rewritten = new StringWriter();
        try (Reader reader = Files.newBufferedReader(file)) {
            for (Node document : yaml.composeAll(reader)) {
                rewritten.write("---\n");
                yaml.serialize(escapeRawScalars(document), rewritten);
            }
        }
        return new YamlConfigSource(file.toUri().toString(), rewritten.toString(), ordinal);
    }

    /**
     * Loads a YAML file as {@link #rawAwareYamlSource(Path, int)} does, at this source's ordinal —
     * for callers that only read the file's properties.
     *
     * @param file the YAML file
     * @return the loaded source
     * @throws IOException if the file cannot be read
     */
    static YamlConfigSource rawAwareYamlSource(Path file) throws IOException {
        return rawAwareYamlSource(file, ORDINAL);
    }

    /**
     * Replaces each {@code !raw} scalar under {@code node} with a plain string whose every {@code
     * $} is escaped as {@code \$}, which the one expansion in the main config turns back into the
     * original text.
     *
     * @param node the node to rewrite
     * @return the rewritten node
     */
    private static Node escapeRawScalars(Node node) {
        if (node instanceof ScalarNode scalar && scalar.getTag().getValue().equals("!raw")) {
            return new ScalarNode(
                    Tag.STR,
                    scalar.getValue().replace("$", "\\$"),
                    scalar.getStartMark(),
                    scalar.getEndMark(),
                    scalar.getScalarStyle());
        }
        if (node instanceof MappingNode mapping) {
            mapping.setValue(
                    mapping.getValue().stream()
                            .map(
                                    tuple ->
                                            new NodeTuple(
                                                    tuple.getKeyNode(),
                                                    escapeRawScalars(tuple.getValueNode())))
                            .toList());
        }
        return node;
    }

    @Override
    public Map<String, String> getProperties() {
        return Map.copyOf(properties);
    }

    @Override
    public @Nullable String getValue(String propertyName) {
        return properties.get(propertyName);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }

    @Override
    public Set<String> getPropertyNames() {
        return properties.keySet();
    }
}
