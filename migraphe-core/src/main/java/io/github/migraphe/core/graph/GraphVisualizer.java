package io.github.migraphe.core.graph;

import java.util.*;

/** マイグレーショングラフをターミナルに可視化するユーティリティ。 初期マイルストーン機能。 */
public final class GraphVisualizer {

    /** グラフをASCIIアートとしてターミナルに出力 */
    public static String visualize(MigrationGraph graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(60)).append("\n");
        sb.append("Migration Graph Visualization\n");
        sb.append("=".repeat(60)).append("\n\n");

        sb.append(String.format("Total Nodes: %d\n", graph.size()));
        sb.append(String.format("Root Nodes: %d\n\n", graph.getRoots().size()));

        // 全ノードをリスト表示
        sb.append("Nodes:\n");
        sb.append("-".repeat(60)).append("\n");
        for (MigrationNode node : graph.allNodes()) {
            sb.append(formatNode(node, graph));
        }

        // 実行プランを表示
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

    /** グラフの統計情報を出力 */
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
