package io.github.migraphe.cli.config.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 環境設定（environments/*.toml）のモデル。
 *
 * <p>環境変数定義を保持します。TOMLファイルの全てのキー=値ペアを変数マップとして扱います。
 */
public final class EnvironmentConfig {
    private final Map<String, String> variables;

    public EnvironmentConfig() {
        this.variables = new HashMap<>();
    }

    /**
     * Jackson用のアノテーション。TOMLの全てのキー=値を変数として追加します。
     *
     * @param key 変数名
     * @param value 変数値
     */
    @JsonAnySetter
    public void setVariable(String key, String value) {
        Objects.requireNonNull(key, "variable key must not be null");
        Objects.requireNonNull(value, "variable value must not be null");
        this.variables.put(key, value);
    }

    /**
     * 環境変数マップを取得します。
     *
     * @return 不変な変数マップ
     */
    public Map<String, String> variables() {
        return Map.copyOf(variables);
    }

    /**
     * 空の環境設定を作成します（テスト用）。
     *
     * @return 空のEnvironmentConfig
     */
    public static EnvironmentConfig empty() {
        return new EnvironmentConfig();
    }
}
