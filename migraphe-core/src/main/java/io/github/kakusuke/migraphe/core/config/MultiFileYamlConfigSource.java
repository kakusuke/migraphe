package io.github.kakusuke.migraphe.core.config;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.smallrye.config.source.yaml.YamlConfigSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jspecify.annotations.Nullable;

/**
 * A {@link ConfigSource} that merges Migraphe's several YAML files into a single flat key space.
 *
 * <p>Each source file is loaded with {@link YamlConfigSource} and its keys are re-namespaced
 * according to the file's role, so that the combined config can be addressed by a single set of
 * property names. The merge happens eagerly in the constructor; the resulting map is exposed at
 * ordinal {@code 100}, the lowest tier in the layering, so that environment files, system
 * properties, OS env and explicit variables can all override it.
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
                YamlConfigSource yamlSource = new YamlConfigSource(file.toUri().toURL());

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
                YamlConfigSource yamlSource = new YamlConfigSource(file.toUri().toURL());

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
