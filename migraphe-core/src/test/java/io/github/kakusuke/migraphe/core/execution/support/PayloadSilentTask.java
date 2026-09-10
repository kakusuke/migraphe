package io.github.kakusuke.migraphe.core.execution.support;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;

/**
 * A task that runs but cannot say what rollback it would record.
 *
 * <p>Deliberately not a {@link io.github.kakusuke.migraphe.api.task.RollbackPayloadProvider}: this
 * stands in for a third-party plugin whose up task only reports its payload by running, which is
 * what amending cannot do.
 */
public record PayloadSilentTask(String content) implements Task {

    @Override
    public Result<TaskResult, String> execute() {
        return Result.ok(TaskResult.withoutDownTask("Executed"));
    }

    @Override
    public String signature() {
        return content;
    }

    @Override
    public String description() {
        return content;
    }
}
