package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.core.config.ConfigLoader;
import io.github.kakusuke.migraphe.core.config.ProjectConfig;
import io.github.kakusuke.migraphe.core.factory.EnvironmentFactory;
import io.github.kakusuke.migraphe.core.factory.MigrationNodeFactory;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import io.smallrye.config.SmallRyeConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Immutable snapshot of everything needed to run migrations for a project.
 *
 * <p>An {@code ExecutionContext} bundles the loaded project configuration, the per-target {@link
 * Environment} instances, the migration nodes, the assembled {@link MigrationGraph}, and the {@link
 * PluginRegistry} used to resolve plugins. Instances are produced by the static {@code load}
 * factory methods, which drive the full pipeline: config loading, environment/node creation,
 * dependency sorting, graph construction, and graph validation. Commands then build an {@link
 * Executor} or {@link StatusService} from this context.
 *
 * @param baseDir the project root directory
 * @param scanRoot the resolved scan-root directory (from {@code project.scan-root})
 * @param config the resolved MicroProfile / SmallRye configuration
 * @param pluginRegistry the registry used to look up plugins by target type
 * @param environments map of target ID to its {@link Environment}
 * @param nodes the migration nodes, sorted in dependency order
 * @param graph the migration graph built from {@code nodes}
 */
public record ExecutionContext(
        Path baseDir,
        Path scanRoot,
        SmallRyeConfig config,
        PluginRegistry pluginRegistry,
        Map<String, Environment> environments,
        List<MigrationNode> nodes,
        MigrationGraph graph) {

    /**
     * Creates the {@link HistoryRepository} for this project.
     *
     * <p>The history target is read from {@code history.target} in the project configuration and
     * resolved against the configured targets; the matching plugin's {@link
     * io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider} then creates the repository
     * against that {@link Environment}.
     *
     * @return the project's history repository
     * @throws IllegalStateException if {@code history.target} names no configured target. Falling
     *     back to an in-memory repository would let a run apply its migrations to the real database
     *     and then discard the record of having done so.
     */
    public HistoryRepository createHistoryRepository() {
        String historyTarget = config.getConfigMapping(ProjectConfig.class).history().target();
        Environment historyEnv = environments.get(historyTarget);
        if (historyEnv == null) {
            throw new IllegalStateException(
                    "history.target '"
                            + historyTarget
                            + "' does not match any configured target. Configured targets: "
                            + new TreeSet<>(environments.keySet()));
        }
        String type = config.getValue("target." + historyTarget + ".type", String.class);
        MigraphePlugin<?> plugin = pluginRegistry.getRequiredPlugin(type);
        return plugin.historyRepositoryProvider().createRepository(historyEnv);
    }

    /**
     * Loads an {@code ExecutionContext} from a project directory.
     *
     * <p>No deployment-environment file is loaded ({@code envName = null}).
     *
     * @param baseDir the project root directory
     * @param pluginRegistry the plugin registry used to resolve plugins by target type
     * @return the loaded execution context
     */
    public static ExecutionContext load(Path baseDir, PluginRegistry pluginRegistry) {
        return load(baseDir, pluginRegistry, null, Collections.emptyMap());
    }

    /**
     * Loads an {@code ExecutionContext} from a project directory with override variables.
     *
     * <p>No deployment-environment file is loaded ({@code envName = null}).
     *
     * @param baseDir the project root directory
     * @param pluginRegistry the plugin registry used to resolve plugins by target type
     * @param variables override variables injected into the SmallRye config at the highest priority
     * @return the loaded execution context
     */
    public static ExecutionContext load(
            Path baseDir, PluginRegistry pluginRegistry, Map<String, String> variables) {
        return load(baseDir, pluginRegistry, null, variables);
    }

    /**
     * Loads an {@code ExecutionContext} from a project directory for a named environment.
     *
     * @param baseDir the project root directory
     * @param pluginRegistry the plugin registry used to resolve plugins by target type
     * @param envName the deployment-environment name (loads {@code environments/<envName>.yaml}),
     *     or {@code null} to load no environment file
     * @return the loaded execution context
     */
    public static ExecutionContext load(
            Path baseDir, PluginRegistry pluginRegistry, @Nullable String envName) {
        return load(baseDir, pluginRegistry, envName, Collections.emptyMap());
    }

    /**
     * Loads an {@code ExecutionContext} from a project directory for a named environment with
     * override variables.
     *
     * <p>This is the primary loader the other overloads delegate to. It loads the configuration,
     * creates all environments and migration nodes, sorts the nodes in dependency order, builds the
     * {@link MigrationGraph}, and validates the graph.
     *
     * @param baseDir the project root directory
     * @param pluginRegistry the plugin registry used to resolve plugins by target type
     * @param envName the deployment-environment name (loads {@code environments/<envName>.yaml}),
     *     or {@code null} to load no environment file
     * @param variables override variables injected into the SmallRye config at the highest priority
     * @return the loaded execution context
     * @throws IllegalStateException if the assembled migration graph is invalid (e.g. cycles or
     *     missing dependencies)
     */
    public static ExecutionContext load(
            Path baseDir,
            PluginRegistry pluginRegistry,
            @Nullable String envName,
            Map<String, String> variables) {
        // 1. Load the YAML configuration via ConfigLoader.
        ConfigLoader configLoader = new ConfigLoader();
        Path scanRoot = configLoader.resolveScanRoot(baseDir);
        SmallRyeConfig config = configLoader.loadConfig(baseDir, envName, variables);

        // 2. Load environment definitions and create all Environments via EnvironmentFactory.
        Map<String, EnvironmentDefinition> environmentDefinitions =
                configLoader.loadEnvironmentDefinitions(config, pluginRegistry);
        EnvironmentFactory environmentFactory = new EnvironmentFactory(pluginRegistry);
        Map<String, Environment> environments =
                environmentFactory.createEnvironments(environmentDefinitions);

        // 3. Load task definitions (mapped to each plugin's specific type) via ConfigLoader.
        Map<NodeId, TaskDefinition<?>> taskDefinitions =
                configLoader.loadTaskDefinitions(baseDir, config, pluginRegistry);

        // 4. Create migration nodes via MigrationNodeFactory.
        MigrationNodeFactory nodeFactory = new MigrationNodeFactory(pluginRegistry, config);
        List<MigrationNode> nodes = nodeFactory.createNodes(taskDefinitions, environments);

        // 5. Build the MigrationGraph (adding nodes in dependency order).
        List<MigrationNode> sortedNodes = sortNodesByDependencies(nodes);
        MigrationGraph graph = MigrationGraph.fromNodesUp(sortedNodes);

        // 6. A cycle means no order exists, so nothing can be built from this. An unresolved
        // dependency is only incompleteness, and a project in that state must still be able to
        // report what happened to it — the commands that would apply something refuse instead.
        if (graph.hasCycle()) {
            throw new IllegalStateException(
                    "Migration graph is invalid: Graph contains a cycle (circular dependency)");
        }

        return new ExecutionContext(
                baseDir, scanRoot, config, pluginRegistry, environments, sortedNodes, graph);
    }

    /**
     * Sorts nodes into dependency order via topological (DFS) sort.
     *
     * @param nodes the unsorted node list
     * @return the nodes sorted so that every dependency precedes its dependents
     */
    private static List<MigrationNode> sortNodesByDependencies(List<MigrationNode> nodes) {

        // Index nodes by ID.
        Map<NodeId, MigrationNode> nodeMap = new HashMap<>();
        for (MigrationNode node : nodes) {
            nodeMap.put(node.id(), node);
        }

        List<MigrationNode> sorted = new ArrayList<>();
        Set<NodeId> visited = new HashSet<>();
        Set<NodeId> visiting = new HashSet<>();

        // Visit each node to perform the topological sort.
        for (MigrationNode node : nodes) {
            if (!visited.contains(node.id())) {
                visitNode(node, nodeMap, visited, visiting, sorted);
            }
        }

        return sorted;
    }

    /**
     * Visits a node depth-first, appending it to {@code sorted} after its dependencies.
     *
     * @param node the node currently being visited
     * @param nodeMap the map of all nodes by ID
     * @param visited the set of fully visited nodes
     * @param visiting the set of nodes currently on the DFS stack (for cycle detection)
     * @param sorted the accumulating sorted result
     * @throws IllegalArgumentException if a circular dependency is detected
     */
    private static void visitNode(
            MigrationNode node,
            Map<NodeId, MigrationNode> nodeMap,
            Set<NodeId> visited,
            Set<NodeId> visiting,
            List<MigrationNode> sorted) {

        if (visiting.contains(node.id())) {
            throw new IllegalArgumentException("Circular dependency detected: " + node.id());
        }

        visiting.add(node.id());

        // Visit dependencies first.
        for (NodeId depId : node.dependencies()) {
            MigrationNode depNode = nodeMap.get(depId);
            if (depNode != null && !visited.contains(depId)) {
                visitNode(depNode, nodeMap, visited, visiting, sorted);
            }
        }

        visiting.remove(node.id());
        visited.add(node.id());
        sorted.add(node);
    }
}
