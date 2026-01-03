package io.github.migraphe.cli.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * ターゲット設定（targets/*.toml）のモデル。
 *
 * <p>マイグレーション対象（データベースなど）への接続情報を定義します。 変数展開（${VAR}）をサポートします。
 */
public record TargetConfig(
        @JsonProperty("type") String type,
        @JsonProperty("jdbc_url") String jdbcUrl,
        @JsonProperty("username") String username,
        @JsonProperty("password") String password) {

    public TargetConfig {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(jdbcUrl, "jdbc_url must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(password, "password must not be null");

        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbc_url must not be blank");
        }
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        // password は空白を許可（空パスワードの場合があるため）
    }
}
