package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.execution.ExecutionListener;
import io.github.kakusuke.migraphe.api.execution.ExecutionPlanInfo;
import io.github.kakusuke.migraphe.api.execution.ExecutionSummary;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SynchronizedExecutionListener")
class SynchronizedExecutionListenerTest {

    @Test
    @DisplayName("デリゲートにメソッド呼び出しが委譲される")
    void delegatesMethodCalls() {
        var recorder = new RecordingListener();
        var sync = new SynchronizedExecutionListener(recorder);

        MigrationNode node =
                SimpleMigrationNode.builder()
                        .id("test-node")
                        .name("Test Node")
                        .environment(SimpleEnvironment.create(EnvironmentId.of("test"), "Test Env"))
                        .upTask(SimpleTask.of("noop"))
                        .build();

        sync.onNodeStarted(node, ExecutionDirection.UP);

        assertThat(recorder.calls).containsExactly("onNodeStarted:test-node:UP");
    }

    private static class RecordingListener implements ExecutionListener {
        final List<String> calls = new ArrayList<>();

        @Override
        public void onPlanCreated(ExecutionPlanInfo plan) {
            calls.add("onPlanCreated");
        }

        @Override
        public void onNodeStarted(MigrationNode node, ExecutionDirection direction) {
            calls.add("onNodeStarted:" + node.id().value() + ":" + direction);
        }

        @Override
        public void onNodeSucceeded(
                MigrationNode node, ExecutionDirection direction, long durationMs) {
            calls.add("onNodeSucceeded:" + node.id().value() + ":" + direction);
        }

        @Override
        public void onNodeSkipped(MigrationNode node, ExecutionDirection direction, String reason) {
            calls.add("onNodeSkipped:" + node.id().value() + ":" + direction);
        }

        @Override
        public void onNodeFailed(
                MigrationNode node,
                ExecutionDirection direction,
                @Nullable String sqlContent,
                String errorMessage) {
            calls.add("onNodeFailed:" + node.id().value() + ":" + direction);
        }

        @Override
        public void onCompleted(ExecutionSummary summary) {
            calls.add("onCompleted");
        }
    }
}
