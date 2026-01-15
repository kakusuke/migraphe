package io.github.kakusuke.migraphe.api.task;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** タスク実行結果。 UP実行時にはシリアライズされたDownTaskを含む。 */
public record TaskResult(String message, @Nullable String serializedDownTask) {
    public TaskResult {
        Objects.requireNonNull(message, "message must not be null");
    }

    /** Down Task なしの結果を作成（DOWN実行時やロールバック非対応の場合） */
    public static TaskResult withoutDownTask(String message) {
        return new TaskResult(message, null);
    }

    /** Down Task ありの結果を作成（UP実行時） */
    public static TaskResult withDownTask(String message, String serializedDownTask) {
        Objects.requireNonNull(serializedDownTask, "serializedDownTask must not be null");
        return new TaskResult(message, serializedDownTask);
    }
}
