package io.github.migraphe.cli.factory;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.environment.EnvironmentConfig;
import io.github.migraphe.api.spi.MigraphePlugin;
import io.github.migraphe.core.config.ConfigurationException;
import io.github.migraphe.core.plugin.PluginRegistry;
import io.smallrye.config.SmallRyeConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.StreamSupport;

/** TargetConfig から Environment を生成する汎用ファクトリ。プラグインを使用して Environment を生成する。 */
public class EnvironmentFactory {

    private final PluginRegistry pluginRegistry;

    public EnvironmentFactory(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    /**
     * 指定されたターゲットIDの Environment を生成する。
     *
     * @param config SmallRyeConfig
     * @param targetId ターゲットID (例: "db1")
     * @return Environment
     * @throws ConfigurationException ターゲット設定が見つからない場合、またはプラグインが見つからない場合
     */
    public Environment createEnvironment(SmallRyeConfig config, String targetId) {
        String prefix = "target." + targetId + ".";

        try {
            String type = config.getValue(prefix + "type", String.class);

            // プラグインを取得
            MigraphePlugin plugin =
                    pluginRegistry
                            .getPlugin(type)
                            .orElseThrow(
                                    () ->
                                            new ConfigurationException(
                                                    "No plugin found for type: "
                                                            + type
                                                            + ". Available types: "
                                                            + pluginRegistry.supportedTypes()));

            // EnvironmentConfig を構築
            EnvironmentConfig envConfig = buildEnvironmentConfig(config, prefix);

            // プラグインの EnvironmentProvider で Environment を生成
            return plugin.environmentProvider().createEnvironment(targetId, envConfig);

        } catch (NoSuchElementException e) {
            throw new ConfigurationException("Target configuration not found: " + targetId, e);
        }
    }

    /**
     * Config から全てのターゲット設定を読み込み、Environment のマップを生成する。
     *
     * @param config SmallRyeConfig
     * @return ターゲットID → Environment のマップ
     */
    public Map<String, Environment> createEnvironments(SmallRyeConfig config) {
        Map<String, Environment> environments = new HashMap<>();

        // "target.*" プレフィックスを持つプロパティからターゲットIDを抽出
        StreamSupport.stream(config.getPropertyNames().spliterator(), false)
                .filter(name -> name.startsWith("target."))
                .map(name -> name.substring("target.".length())) // "db1.type" → "db1.type"
                .map(name -> name.split("\\.")[0]) // "db1.type" → "db1"
                .distinct()
                .forEach(
                        targetId -> {
                            Environment env = createEnvironment(config, targetId);
                            environments.put(targetId, env);
                        });

        return environments;
    }

    /**
     * SmallRyeConfig からターゲット固有のプロパティを抽出し、EnvironmentConfig を構築する。
     *
     * @param config SmallRyeConfig
     * @param prefix プロパティのプレフィックス（例: "target.db1."）
     * @return EnvironmentConfig
     */
    private EnvironmentConfig buildEnvironmentConfig(SmallRyeConfig config, String prefix) {
        Map<String, String> properties = new HashMap<>();

        // prefix で始まるプロパティを抽出
        StreamSupport.stream(config.getPropertyNames().spliterator(), false)
                .filter(name -> name.startsWith(prefix))
                .forEach(
                        name -> {
                            String key =
                                    name.substring(
                                            prefix.length()); // "target.db1.jdbc_url" → "jdbc_url"
                            String value = config.getValue(name, String.class);
                            properties.put(key, value);
                        });

        return EnvironmentConfig.of(properties);
    }
}
