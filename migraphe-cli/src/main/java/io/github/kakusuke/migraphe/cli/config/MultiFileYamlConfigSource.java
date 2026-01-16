package io.github.kakusuke.migraphe.cli.config;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.core.config.ConfigurationException;
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
 * 複数の YAML ファイルを統合する ConfigSource。
 *
 * <p>プレフィックス戦略:
 *
 * <ul>
 *   <li>migraphe.yaml → プレフィックスなし (project.*, history.*)
 *   <li>targets/db1.yaml → "target.db1.*" プレフィックス
 *   <li>tasks/db1/create_users.yaml → "task.\"db1/create_users\".*" プレフィックス
 * </ul>
 */
public class MultiFileYamlConfigSource implements ConfigSource {

    private static final String NAME = "MultiFileYamlConfigSource";
    private static final int ORDINAL = 100;

    private final Map<String, String> properties = new HashMap<>();

    /**
     * 複数の YAML ファイルをロードして統合する ConfigSource を構築する。
     *
     * @param projectConfigFile migraphe.yaml のパス
     * @param targetFiles targets/*.yaml のリスト
     * @param taskFiles タスクファイルのマップ (TaskId → ファイルパス)
     * @throws ConfigurationException YAML ファイルの読み込みに失敗した場合
     */
    public MultiFileYamlConfigSource(
            Path projectConfigFile, List<Path> targetFiles, Map<NodeId, Path> taskFiles) {
        loadProjectConfig(projectConfigFile);
        loadTargetConfigs(targetFiles);
        loadTaskConfigs(taskFiles);
    }

    /**
     * migraphe.yaml をロードする (プレフィックスなし)。
     *
     * @param file migraphe.yaml のパス
     * @throws ConfigurationException YAML ファイルの読み込みに失敗した場合
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
     * targets/*.yaml をロードする ("target.{targetId}.*" プレフィックス)。
     *
     * @param files targets/*.yaml のリスト
     * @throws ConfigurationException YAML ファイルの読み込みに失敗した場合
     */
    private void loadTargetConfigs(List<Path> files) {
        for (Path file : files) {
            try {
                // ファイル名から targetId を抽出 (db1.yaml → "db1")
                String targetId = file.getFileName().toString().replaceAll("\\.yaml$", "");

                // YAML ロード
                YamlConfigSource yamlSource = new YamlConfigSource(file.toUri().toURL());

                // プレフィックス付与して統合
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
     * tasks/**\/*.yaml をロードする ("task.\"{taskId}\".*" プレフィックス)。
     *
     * @param taskFiles タスクファイルのマップ (TaskId → ファイルパス)
     * @throws ConfigurationException YAML ファイルの読み込みに失敗した場合
     */
    private void loadTaskConfigs(Map<NodeId, Path> taskFiles) {
        for (Map.Entry<NodeId, Path> entry : taskFiles.entrySet()) {
            NodeId taskId = entry.getKey();
            Path file = entry.getValue();

            try {
                // YAML ロード
                YamlConfigSource yamlSource = new YamlConfigSource(file.toUri().toURL());

                // プレフィックス付与して統合
                // Task ID にスラッシュが含まれる場合、"task.\"db1/create_users\".*" 形式
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
