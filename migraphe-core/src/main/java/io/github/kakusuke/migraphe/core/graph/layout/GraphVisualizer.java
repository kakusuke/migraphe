package io.github.kakusuke.migraphe.core.graph.layout;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.core.graph.ExecutionPlan;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.graph.TopologicalSort;
import java.util.*;

/**
 * Utility that renders a {@link MigrationGraph} as human-readable text for terminal display.
 *
 * <p>Unlike the {@link ExecutionGraphView} pipeline, which draws a compact ASCII tree, this is an
 * early-milestone text report: it lists every node with its environment, description, and
 * dependency/dependent edges, followed by the parallel execution plan and per-environment
 * statistics. This is a static utility class and cannot be instantiated.
 */
public final class GraphVisualizer {

    /** Creates a new {@code GraphVisualizer}. */
    public GraphVisualizer() {}

    /**
     * Renders the graph as a multi-section ASCII text report (header, node list, execution plan).
     *
     * <p>If a valid execution plan cannot be created (e.g. the graph contains a cycle), an error
     * notice with the failure reason is appended instead of the plan.
     *
     * @param graph the migration graph to visualize
     * @return the rendered report text
     */
    public static String visualize(MigrationGraph graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(60)).append("\n");
        sb.append("Migration Graph Visualization\n");
        sb.append("=".repeat(60)).append("\n\n");

        sb.append(String.format("Total Nodes: %d\n", graph.size()));
        sb.append(String.format("Root Nodes: %d\n\n", graph.getRoots().size()));

        // List all nodes
        sb.append("Nodes:\n");
        sb.append("-".repeat(60)).append("\n");
        for (MigrationNode node : graph.allNodes()) {
            sb.append(formatNode(node, graph));
        }

        // Display the execution plan
        try {
            ExecutionPlan plan = TopologicalSort.createExecutionPlan(graph);
            sb.append("\n").append("=".repeat(60)).append("\n");
            sb.append("Execution Plan (Parallel Levels)\n");
            sb.append("=".repeat(60)).append("\n");
            sb.append(visualizeExecutionPlan(plan));
        } catch (IllegalStateException e) {
            sb.append("\n⚠ ERROR: Cannot create execution plan\n");
            sb.append("  Reason: ").append(e.getMessage()).append("\n");
        }

        return sb.toString();
    }

    private static String formatNode(MigrationNode node, MigrationGraph graph) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("📦 [%s] %s\n", node.id().value(), node.name()));
        sb.append(String.format("   Env: %s\n", node.environment().name()));

        if (node.description() != null && !node.description().isBlank()) {
            sb.append(String.format("   Desc: %s\n", node.description()));
        }

        if (!node.dependencies().isEmpty()) {
            sb.append("   Dependencies: ");
            sb.append(
                    String.join(", ", node.dependencies().stream().map(id -> id.value()).toList()));
            sb.append("\n");
        } else {
            sb.append("   Dependencies: (none - root node)\n");
        }

        Set<NodeId> dependents = graph.getDependents(node.id());
        if (!dependents.isEmpty()) {
            sb.append("   Dependents: ");
            sb.append(String.join(", ", dependents.stream().map(id -> id.value()).toList()));
            sb.append("\n");
        }

        sb.append("\n");
        return sb.toString();
    }

    private static String visualizeExecutionPlan(ExecutionPlan plan) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("\nTotal Levels: %d\n", plan.levelCount()));
        sb.append(String.format("Max Parallelism: %d\n\n", plan.maxParallelism()));

        for (int i = 0; i < plan.levels().size(); i++) {
            var level = plan.levels().get(i);
            sb.append(
                    String.format("Level %d (%d nodes - can run in parallel):\n", i, level.size()));

            for (MigrationNode node : level.nodes()) {
                sb.append(
                        String.format(
                                "  → %s [%s] @ %s\n",
                                node.name(), node.id().value(), node.environment().name()));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Renders summary statistics for the graph: total node count, root count, and the number of
     * nodes per environment.
     *
     * @param graph the migration graph to summarize
     * @return the rendered statistics text
     */
    public static String statistics(MigrationGraph graph) {
        Map<String, Long> envCounts = new HashMap<>();

        for (MigrationNode node : graph.allNodes()) {
            String envName = node.environment().name();
            envCounts.merge(envName, 1L, Long::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Graph Statistics:\n");
        sb.append("-".repeat(40)).append("\n");
        sb.append(String.format("Total Nodes: %d\n", graph.size()));
        sb.append(String.format("Root Nodes: %d\n", graph.getRoots().size()));
        sb.append("\nNodes per Environment:\n");
        envCounts.forEach((env, count) -> sb.append(String.format("  %s: %d\n", env, count)));

        return sb.toString();
    }
}
