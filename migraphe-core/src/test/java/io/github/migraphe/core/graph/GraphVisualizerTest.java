package io.github.migraphe.core.graph;

import static io.github.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.migraphe.core.graph.TestHelpers.TestEnvironment;
import org.junit.jupiter.api.Test;

class GraphVisualizerTest {

    @Test
    void shouldVisualizeSimpleGraph() {
        // given: 単純な1ノードのグラフ
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode node = node("node-1").name("Create users table").build();
        graph.addNode(node);

        // when
        String result = GraphVisualizer.visualize(graph);

        // then
        System.out.println("\n=== Simple Graph ===");
        System.out.println(result);

        assertThat(result).isNotEmpty();
        assertThat(result).contains("Migration Graph Visualization");
        assertThat(result).contains("Total Nodes: 1");
        assertThat(result).contains("Create users table");
    }

    @Test
    void shouldVisualizeLinearDependencies() {
        // given: 線形依存関係のグラフ (A -> B -> C)
        MigrationGraph graph = MigrationGraph.create();
        NodeId idA = NodeId.of("A");
        NodeId idB = NodeId.of("B");

        MigrationNode nodeA = node("A").name("Create schema").build();
        MigrationNode nodeB = node("B").name("Create tables").dependencies(idA).build();
        MigrationNode nodeC = node("C").name("Insert data").dependencies(idB).build();

        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        // when
        String result = GraphVisualizer.visualize(graph);

        // then
        System.out.println("\n=== Linear Dependencies Graph ===");
        System.out.println(result);

        assertThat(result).contains("Total Nodes: 3");
        assertThat(result).contains("Root Nodes: 1");
        assertThat(result).contains("Total Levels: 3");
        assertThat(result).contains("Max Parallelism: 1");
    }

    @Test
    void shouldVisualizeParallelExecution() {
        // given: 並列実行可能なグラフ (ダイヤモンド構造)
        //      A
        //     / \
        //    B   C
        //     \ /
        //      D
        MigrationGraph graph = MigrationGraph.create();
        NodeId idA = NodeId.of("A");
        NodeId idB = NodeId.of("B");
        NodeId idC = NodeId.of("C");

        MigrationNode nodeA = node("A").name("Create schema").build();
        MigrationNode nodeB = node("B").name("Create users table").dependencies(idA).build();
        MigrationNode nodeC = node("C").name("Create products table").dependencies(idA).build();
        MigrationNode nodeD = node("D").name("Create orders table").dependencies(idB, idC).build();

        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);
        graph.addNode(nodeD);

        // when
        String result = GraphVisualizer.visualize(graph);

        // then
        System.out.println("\n=== Parallel Execution Graph (Diamond) ===");
        System.out.println(result);

        assertThat(result).contains("Total Nodes: 4");
        assertThat(result).contains("Root Nodes: 1");
        assertThat(result).contains("Total Levels: 3");
        assertThat(result).contains("Max Parallelism: 2");
        assertThat(result).contains("can run in parallel");
    }

    @Test
    void shouldVisualizeMultipleEnvironments() {
        // given: 複数環境のグラフ
        TestEnvironment dev = new TestEnvironment("development");
        TestEnvironment stg = new TestEnvironment("staging");
        TestEnvironment prd = new TestEnvironment("production");

        MigrationGraph graph = MigrationGraph.create();

        MigrationNode node1 = node("dev-1").name("Setup dev schema").environment(dev).build();
        MigrationNode node2 = node("stg-1").name("Setup staging schema").environment(stg).build();
        MigrationNode node3 =
                node("prd-1").name("Setup production schema").environment(prd).build();

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);

        // when
        String result = GraphVisualizer.visualize(graph);

        // then
        System.out.println("\n=== Multiple Environments Graph ===");
        System.out.println(result);

        assertThat(result).contains("Total Nodes: 3");
        assertThat(result).contains("development");
        assertThat(result).contains("staging");
        assertThat(result).contains("production");
    }

    @Test
    void shouldVisualizeComplexGraph() {
        // given: 複雑なグラフ
        //       A
        //      /|\
        //     B C D
        //     |X| |
        //     E F G
        //      \|/
        //       H
        MigrationGraph graph = MigrationGraph.create();
        NodeId idA = NodeId.of("A");
        NodeId idB = NodeId.of("B");
        NodeId idC = NodeId.of("C");
        NodeId idD = NodeId.of("D");
        NodeId idE = NodeId.of("E");
        NodeId idF = NodeId.of("F");
        NodeId idG = NodeId.of("G");

        MigrationNode nodeA = node("A").name("Root migration").build();
        MigrationNode nodeB = node("B").name("Schema A").dependencies(idA).build();
        MigrationNode nodeC = node("C").name("Schema B").dependencies(idA).build();
        MigrationNode nodeD = node("D").name("Schema C").dependencies(idA).build();
        MigrationNode nodeE = node("E").name("Table A1").dependencies(idB, idC).build();
        MigrationNode nodeF = node("F").name("Table B1").dependencies(idC, idD).build();
        MigrationNode nodeG = node("G").name("Table C1").dependencies(idD).build();
        MigrationNode nodeH = node("H").name("Final migration").dependencies(idE, idF, idG).build();

        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);
        graph.addNode(nodeD);
        graph.addNode(nodeE);
        graph.addNode(nodeF);
        graph.addNode(nodeG);
        graph.addNode(nodeH);

        // when
        String result = GraphVisualizer.visualize(graph);

        // then
        System.out.println("\n=== Complex Graph ===");
        System.out.println(result);

        assertThat(result).contains("Total Nodes: 8");
        assertThat(result).contains("Root Nodes: 1");
        assertThat(result).contains("Total Levels: 4");
        assertThat(result).contains("Max Parallelism: 3");
    }

    @Test
    void shouldShowStatistics() {
        // given: 複数環境のグラフ
        TestEnvironment dev = new TestEnvironment("development");
        TestEnvironment prd = new TestEnvironment("production");

        MigrationGraph graph = MigrationGraph.create();

        graph.addNode(node("dev-1").name("Dev migration 1").environment(dev).build());
        graph.addNode(node("dev-2").name("Dev migration 2").environment(dev).build());
        graph.addNode(node("prd-1").name("Prod migration 1").environment(prd).build());

        // when
        String result = GraphVisualizer.statistics(graph);

        // then
        System.out.println("\n=== Graph Statistics ===");
        System.out.println(result);

        assertThat(result).contains("Total Nodes: 3");
        assertThat(result).contains("Root Nodes: 3");
        assertThat(result).contains("development: 2");
        assertThat(result).contains("production: 1");
    }
}
