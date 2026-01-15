package io.github.kakusuke.migraphe.cli.factory;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.core.config.ConfigurationException;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.util.LinkedHashMap;
import java.util.Map;

/** EnvironmentDefinition から Environment を生成する汎用ファクトリ。プラグインを使用して Environment を生成する。 */
public class EnvironmentFactory {

    private final PluginRegistry pluginRegistry;

    public EnvironmentFactory(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    /**
     * EnvironmentDefinition から Environment を生成する。
     *
     * @param targetId ターゲットID (例: "db1")
     * @param definition 環境定義
     * @return Environment
     * @throws ConfigurationException プラグインが見つからない場合
     */
    public Environment createEnvironment(String targetId, EnvironmentDefinition definition) {
        String type = definition.type();

        // プラグインを取得
        MigraphePlugin<?> plugin = pluginRegistry.getRequiredPlugin(type);

        // プラグインの EnvironmentProvider で Environment を生成
        return plugin.environmentProvider().createEnvironment(targetId, definition);
    }

    /**
     * EnvironmentDefinition のマップから Environment のマップを生成する。
     *
     * @param definitions targetId → EnvironmentDefinition のマップ
     * @return ターゲットID → Environment のマップ
     */
    public Map<String, Environment> createEnvironments(
            Map<String, EnvironmentDefinition> definitions) {
        Map<String, Environment> environments = new LinkedHashMap<>();

        for (Map.Entry<String, EnvironmentDefinition> entry : definitions.entrySet()) {
            String targetId = entry.getKey();
            EnvironmentDefinition definition = entry.getValue();
            Environment env = createEnvironment(targetId, definition);
            environments.put(targetId, env);
        }

        return environments;
    }
}
