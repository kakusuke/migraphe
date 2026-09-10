package io.github.kakusuke.migraphe.core.execution.support;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.task.RollbackPayloadProvider;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;

/**
 * ロールバックペイロードを実行せずに報告する UP タスク。
 *
 * <p>{@code SimpleTask} も payload を報告するが、metadata は常に {@code null} を返す。両方が非 null の経路を測れるのはこちらだけ。
 */
public record PayloadProvidingTask(String downSql, String metadata)
        implements Task, RollbackPayloadProvider {

    @Override
    public Result<TaskResult, String> execute() {
        return Result.ok(TaskResult.withDownTask("Executed", downSql, metadata));
    }

    @Override
    public String signature() {
        return downSql;
    }

    @Override
    public String description() {
        return "UP with payload";
    }

    @Override
    public String serializedDownTask() {
        return downSql;
    }

    @Override
    public String pluginMetadata() {
        return metadata;
    }
}
