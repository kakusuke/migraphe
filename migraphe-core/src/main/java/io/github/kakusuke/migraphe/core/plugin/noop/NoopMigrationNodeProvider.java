package io.github.kakusuke.migraphe.core.plugin.noop;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.Set;

/** noop MigrationNode を生成する Provider。何もせず成功するタスクを返す。 */
public final class NoopMigrationNodeProvider implements MigrationNodeProvider<String> {

    @Override
    public MigrationNode createNode(
            NodeId nodeId,
            TaskDefinition<String> task,
            Set<NodeId> dependencies,
            Environment environment) {

        var builder =
                SimpleMigrationNode.builder()
                        .id(nodeId)
                        .name(task.name())
                        .environment(environment)
                        .dependencies(dependencies)
                        .upTask(SimpleTask.of(task.up()));

        task.description().ifPresent(builder::description);
        task.down()
                .filter(s -> !s.isBlank())
                .ifPresent(down -> builder.downTask(SimpleTask.withDownTask(down, down)));

        return builder.build();
    }
}
