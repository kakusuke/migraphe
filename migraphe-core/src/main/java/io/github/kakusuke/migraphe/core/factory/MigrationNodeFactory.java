package io.github.kakusuke.migraphe.core.factory;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.core.config.ConfigurationException;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import io.smallrye.config.SmallRyeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generic factory that builds {@link MigrationNode}s from {@link TaskDefinition}s via plugins.
 *
 * <p>For each task definition, the factory looks up the target's {@code type} in the configuration,
 * resolves the matching {@link MigraphePlugin} from the {@link PluginRegistry}, resolves the task's
 * declared dependencies into {@link NodeId}s (a framework responsibility, not the plugin's), and
 * delegates node creation to the plugin's {@link
 * io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider}.
 */
public class MigrationNodeFactory {

    private final PluginRegistry pluginRegistry;
    private final SmallRyeConfig config;

    /**
     * Creates a node factory.
     *
     * @param pluginRegistry the registry used to resolve plugins by target type
     * @param config the configuration used to read each target's {@code type}
     */
    public MigrationNodeFactory(PluginRegistry pluginRegistry, SmallRyeConfig config) {
        this.pluginRegistry = pluginRegistry;
        this.config = config;
    }

    /**
     * Builds a single {@link MigrationNode} from a task definition.
     *
     * @param taskDef the task definition describing the node's target, dependencies, and payload
     * @param nodeId the ID to assign to the created node
     * @param target the target in which the node will run
     * @return the created migration node
     */
    public MigrationNode createNode(TaskDefinition<?> taskDef, NodeId nodeId, Target target) {

        // Read the target's type to select the plugin.
        String targetId = taskDef.target();
        String type = config.getValue("target." + targetId + ".type", String.class);

        MigraphePlugin<?> plugin = pluginRegistry.getRequiredPlugin(type);

        @SuppressWarnings("unchecked")
        MigraphePlugin<Object> typedPlugin = (MigraphePlugin<Object>) plugin;

        // Resolve dependencies (a framework responsibility).
        Set<NodeId> dependencies = resolveDependencies(taskDef);

        // Create the node via the plugin's MigrationNodeProvider.
        @SuppressWarnings("unchecked")
        TaskDefinition<Object> typedTaskDef = (TaskDefinition<Object>) taskDef;
        return typedPlugin
                .migrationNodeProvider()
                .createNode(nodeId, typedTaskDef, dependencies, target);
    }

    /**
     * Builds a list of {@link MigrationNode}s from multiple task definitions.
     *
     * @param taskDefinitions map of node ID to its task definition
     * @param targets map of target ID to its {@link Target}
     * @return the list of created migration nodes
     * @throws ConfigurationException if a task's target has no corresponding {@link Target}
     */
    public List<MigrationNode> createNodes(
            Map<NodeId, TaskDefinition<?>> taskDefinitions, Map<String, Target> targets) {

        List<MigrationNode> nodes = new ArrayList<>();

        for (Map.Entry<NodeId, TaskDefinition<?>> entry : taskDefinitions.entrySet()) {
            NodeId nodeId = entry.getKey();
            TaskDefinition<?> taskDef = entry.getValue();

            // Look up the Target by target ID.
            String targetId = taskDef.target();
            Target target = targets.get(targetId);

            if (target == null) {
                throw new ConfigurationException("No such target: " + targetId);
            }

            MigrationNode node = createNode(taskDef, nodeId, target);
            nodes.add(node);
        }

        return nodes;
    }

    /**
     * Resolves a task definition's declared dependency names into {@link NodeId}s.
     *
     * @param taskDef the task definition whose dependencies are resolved
     * @return the set of dependency node IDs, or an empty set when none are declared
     */
    private Set<NodeId> resolveDependencies(TaskDefinition<?> taskDef) {
        return taskDef.dependencies()
                .map(deps -> deps.stream().map(NodeId::of).collect(Collectors.toSet()))
                .orElse(Set.of());
    }
}
