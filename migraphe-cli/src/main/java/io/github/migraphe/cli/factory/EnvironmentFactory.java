package io.github.migraphe.cli.factory;

import io.github.migraphe.core.config.ConfigurationException;
import io.github.migraphe.postgresql.PostgreSQLEnvironment;
import io.smallrye.config.SmallRyeConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.StreamSupport;

/** TargetConfig から PostgreSQLEnvironment を生成するファクトリ。 */
public class EnvironmentFactory {

    /**
     * 指定されたターゲットIDの Environment を生成する。
     *
     * @param config SmallRyeConfig
     * @param targetId ターゲットID (例: "db1")
     * @return PostgreSQLEnvironment
     * @throws ConfigurationException ターゲット設定が見つからない場合
     */
    public PostgreSQLEnvironment createEnvironment(SmallRyeConfig config, String targetId) {
        String prefix = "target." + targetId + ".";

        try {
            String type = config.getValue(prefix + "type", String.class);
            String jdbcUrl = config.getValue(prefix + "jdbc_url", String.class);
            String username = config.getValue(prefix + "username", String.class);
            String password = config.getValue(prefix + "password", String.class);

            // 現時点では postgresql のみサポート
            if (!"postgresql".equals(type)) {
                throw new ConfigurationException(
                        "Unsupported target type: "
                                + type
                                + " (currently only 'postgresql' is supported)");
            }

            return PostgreSQLEnvironment.create(targetId, jdbcUrl, username, password);

        } catch (NoSuchElementException e) {
            throw new ConfigurationException("Target configuration not found: " + targetId, e);
        }
    }

    /**
     * Config から全てのターゲット設定を読み込み、Environment のマップを生成する。
     *
     * @param config SmallRyeConfig
     * @return ターゲットID → PostgreSQLEnvironment のマップ
     */
    public Map<String, PostgreSQLEnvironment> createEnvironments(SmallRyeConfig config) {
        Map<String, PostgreSQLEnvironment> environments = new HashMap<>();

        // "target.*" プレフィックスを持つプロパティからターゲットIDを抽出
        StreamSupport.stream(config.getPropertyNames().spliterator(), false)
                .filter(name -> name.startsWith("target."))
                .map(name -> name.substring("target.".length())) // "db1.type" → "db1.type"
                .map(name -> name.split("\\.")[0]) // "db1.type" → "db1"
                .distinct()
                .forEach(
                        targetId -> {
                            PostgreSQLEnvironment env = createEnvironment(config, targetId);
                            environments.put(targetId, env);
                        });

        return environments;
    }
}
