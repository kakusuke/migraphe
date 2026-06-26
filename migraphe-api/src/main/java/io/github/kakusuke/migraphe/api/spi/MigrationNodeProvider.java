package io.github.kakusuke.migraphe.api.spi;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.Set;

/**
 * Provider that constructs {@link MigrationNode} instances from task configuration.
 *
 * <p>This is one of the providers a {@link MigraphePlugin} exposes (via {@link
 * MigraphePlugin#migrationNodeProvider()}). The runtime binds a task's YAML to the plugin's {@link
 * TaskDefinition} subtype, resolves the task's dependencies into a set of {@link NodeId}s, and then
 * calls {@link #createNode(NodeId, TaskDefinition, Set, Environment)} to build the node that will
 * participate in the migration graph.
 *
 * <p>Implementors turn a {@link TaskDefinition} into a concrete {@link MigrationNode}, wiring up
 * its UP and DOWN tasks. The framework has already resolved the dependency set, so implementors
 * should simply attach it to the node rather than re-deriving it.
 *
 * @param <T> the type of the UP/DOWN action carried by the {@link TaskDefinition}
 * @see MigraphePlugin#migrationNodeProvider()
 * @see MigrationNode
 * @see TaskDefinition
 */
public interface MigrationNodeProvider<T> {

    /**
     * Creates a {@link MigrationNode} from a task definition.
     *
     * @param nodeId the unique identifier to assign to the created node
     * @param task the plugin-specific task definition (name, target, UP/DOWN actions, etc.)
     * @param dependencies the IDs of the nodes this node depends on, already resolved by the
     *     framework
     * @param environment the environment this node belongs to
     * @return the constructed {@link MigrationNode} instance
     */
    MigrationNode createNode(
            NodeId nodeId,
            TaskDefinition<T> task,
            Set<NodeId> dependencies,
            Environment environment);
}
