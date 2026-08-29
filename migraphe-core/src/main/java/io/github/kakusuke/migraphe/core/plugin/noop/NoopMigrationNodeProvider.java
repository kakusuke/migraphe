package io.github.kakusuke.migraphe.core.plugin.noop;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.Set;

/**
 * {@link MigrationNodeProvider} for the {@code "noop"} plugin.
 *
 * <p>Builds a {@link SimpleMigrationNode} backed by {@link SimpleTask} instances that do nothing
 * and always succeed. The task's {@code up} text becomes the UP task; when a non-blank {@code down}
 * text is configured it becomes the DOWN task (with the same text serialized for rollback),
 * otherwise the node has no DOWN task. The optional task description, if present, is copied onto
 * the node.
 *
 * @see NoopPlugin
 */
public final class NoopMigrationNodeProvider implements MigrationNodeProvider<String> {

    /** Creates a new {@code NoopMigrationNodeProvider}. */
    public NoopMigrationNodeProvider() {}

    /**
     * Creates a noop {@link MigrationNode} from the bound task definition.
     *
     * @param nodeId the ID to assign to the node
     * @param task the bound task definition supplying name, description, and UP/DOWN text
     * @param dependencies the IDs of the nodes this node depends on
     * @param environment the environment the node runs against
     * @return a {@link SimpleMigrationNode} with no-op UP and (optional) DOWN tasks
     */
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
        if (task instanceof NoopTaskDefinition noopTask) {
            noopTask.noWayBack().filter(r -> !r.isBlank()).ifPresent(builder::noWayBack);
        }

        return builder.build();
    }
}
