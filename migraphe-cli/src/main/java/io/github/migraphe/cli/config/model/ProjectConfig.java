package io.github.migraphe.cli.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * プロジェクト設定（migraphe.toml）のモデル。
 *
 * <p>プロジェクト全体の設定と履歴保存先ターゲットを定義します。
 */
public record ProjectConfig(
        @JsonProperty("project") ProjectSection project, @JsonProperty("history") HistorySection history) {

    public ProjectConfig {
        Objects.requireNonNull(project, "project section must not be null");
        Objects.requireNonNull(history, "history section must not be null");
    }

    public record ProjectSection(@JsonProperty("name") String name) {
        public ProjectSection {
            Objects.requireNonNull(name, "project name must not be null");
            if (name.isBlank()) {
                throw new IllegalArgumentException("project name must not be blank");
            }
        }
    }

    public record HistorySection(@JsonProperty("target") String target) {
        public HistorySection {
            Objects.requireNonNull(target, "history target must not be null");
            if (target.isBlank()) {
                throw new IllegalArgumentException("history target must not be blank");
            }
        }
    }
}
