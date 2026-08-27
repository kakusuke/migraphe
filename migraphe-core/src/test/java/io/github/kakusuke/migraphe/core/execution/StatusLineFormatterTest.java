package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.core.execution.StatusService.NodeStatus;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StatusLineFormatter")
class StatusLineFormatterTest {

    private final Environment testEnv =
            SimpleEnvironment.create(EnvironmentId.of("test"), "Test Environment");

    @Test
    @DisplayName("実行済みは所要時間と日時を括弧で添え、未実行はマーカーとラベルだけを出す")
    void shouldRenderExecutedAndPendingNodes() {
        MigrationNode executedNode = createNode("db1/001_create", "Create users");
        ExecutionRecord record =
                ExecutionRecord.upSuccess(
                        executedNode.id(), testEnv.id(), executedNode.name(), null, 250L);

        assertThat(StatusLineFormatter.format(new NodeStatus(executedNode, true, record)))
                .startsWith("[✓] db1/001_create - Create users (250ms, ")
                .endsWith(")");

        MigrationNode pendingNode = createNode("db1/002_index", "Add index");

        assertThat(StatusLineFormatter.format(new NodeStatus(pendingNode, false, null)))
                .isEqualTo("[ ] db1/002_index - Add index");
    }

    private MigrationNode createNode(String id, String name) {
        Task upTask = SimpleTask.of("UP: " + name);
        Task downTask = SimpleTask.of("DOWN: " + name);
        return SimpleMigrationNode.builder()
                .id(NodeId.of(id))
                .name(name)
                .environment(testEnv)
                .dependencies(Set.of())
                .upTask(upTask)
                .downTask(downTask)
                .build();
    }
}
