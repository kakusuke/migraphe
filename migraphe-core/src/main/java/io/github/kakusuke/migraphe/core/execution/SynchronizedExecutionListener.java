package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.execution.ExecutionListener;
import io.github.kakusuke.migraphe.api.execution.ExecutionPlanInfo;
import io.github.kakusuke.migraphe.api.execution.ExecutionSummary;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import org.jspecify.annotations.Nullable;

public final class SynchronizedExecutionListener implements ExecutionListener {

    private final ExecutionListener delegate;

    public SynchronizedExecutionListener(ExecutionListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onPlanCreated(ExecutionPlanInfo plan) {
        synchronized (delegate) {
            delegate.onPlanCreated(plan);
        }
    }

    @Override
    public void onNodeStarted(MigrationNode node, ExecutionDirection direction) {
        synchronized (delegate) {
            delegate.onNodeStarted(node, direction);
        }
    }

    @Override
    public void onNodeSucceeded(MigrationNode node, ExecutionDirection direction, long durationMs) {
        synchronized (delegate) {
            delegate.onNodeSucceeded(node, direction, durationMs);
        }
    }

    @Override
    public void onNodeSkipped(MigrationNode node, ExecutionDirection direction, String reason) {
        synchronized (delegate) {
            delegate.onNodeSkipped(node, direction, reason);
        }
    }

    @Override
    public void onNodeFailed(
            MigrationNode node,
            ExecutionDirection direction,
            @Nullable String sqlContent,
            String errorMessage) {
        synchronized (delegate) {
            delegate.onNodeFailed(node, direction, sqlContent, errorMessage);
        }
    }

    @Override
    public void onCompleted(ExecutionSummary summary) {
        synchronized (delegate) {
            delegate.onCompleted(summary);
        }
    }
}
