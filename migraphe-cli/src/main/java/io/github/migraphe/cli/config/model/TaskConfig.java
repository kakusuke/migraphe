package io.github.migraphe.cli.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * タスク設定（tasks/*.toml）のモデル。
 *
 * <p>マイグレーションタスクの定義（名前、対象、依存関係、UP/DOWN SQL）を保持します。
 */
public record TaskConfig(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("target") String target,
        @JsonProperty("dependencies") List<String> dependencies,
        @JsonProperty("up") SqlBlock up,
        @JsonProperty("down") SqlBlock down) {

    public TaskConfig {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(up, "up must not be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (target.isBlank()) {
            throw new IllegalArgumentException("target must not be blank");
        }

        // description は null 許可、空文字列にデフォルト化
        description = (description != null) ? description : "";

        // dependencies は null の場合は空リストにデフォルト化
        dependencies = (dependencies != null) ? List.copyOf(dependencies) : List.of();
    }

    /**
     * DOWN SQLブロックをOptionalで返します。
     *
     * @return DOWN SQLブロック（存在する場合）
     */
    public Optional<SqlBlock> downOptional() {
        return Optional.ofNullable(down);
    }

    /**
     * SQLブロック（[up] または [down] セクション）。
     *
     * @param sql SQL文
     */
    public record SqlBlock(@JsonProperty("sql") String sql) {
        public SqlBlock {
            Objects.requireNonNull(sql, "sql must not be null");
            if (sql.isBlank()) {
                throw new IllegalArgumentException("sql must not be blank");
            }
        }
    }
}
