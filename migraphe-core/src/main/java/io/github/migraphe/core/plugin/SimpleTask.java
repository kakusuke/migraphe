package io.github.migraphe.core.plugin;

import io.github.migraphe.core.common.Result;
import io.github.migraphe.core.task.Task;
import io.github.migraphe.core.task.TaskResult;
import java.util.Objects;
import java.util.Optional;

/** Task のシンプルなリファレンス実装。 プラグイン開発者はこれを参考に独自の実装を作成できる。 */
public final class SimpleTask implements Task {
    private final String description;
    private final Optional<String> serializedDownTask;

    private SimpleTask(String description, Optional<String> serializedDownTask) {
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.serializedDownTask =
                Objects.requireNonNull(serializedDownTask, "serializedDownTask must not be null");
    }

    @Override
    public Result<TaskResult, String> execute() {
        // シンプルな実装：常に成功を返す
        return Result.ok(new TaskResult("Executed: " + description, serializedDownTask));
    }

    @Override
    public String description() {
        return description;
    }

    public static SimpleTask of(String description) {
        return new SimpleTask(description, Optional.empty());
    }

    public static SimpleTask withDownTask(String description, String serializedDownTask) {
        return new SimpleTask(description, Optional.of(serializedDownTask));
    }
}
