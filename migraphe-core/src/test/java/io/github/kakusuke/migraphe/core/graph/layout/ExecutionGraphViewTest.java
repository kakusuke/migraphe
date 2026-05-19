package io.github.kakusuke.migraphe.core.graph.layout;

import static io.github.kakusuke.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExecutionGraphView")
class ExecutionGraphViewTest {

    @Nested
    @DisplayName("toString()")
    class ToStringTest {

        @Test
        @DisplayName("単一ノードを ● id - name 形式で出力する")
        void shouldRenderSingleNode() {
            MigrationNode nodeA = node("a").name("Create users table").build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            String text = view.toString();

            assertThat(text).isEqualTo("● a - Create users table\n");
        }

        @Test
        @DisplayName("複数ノードを順番に出力する")
        void shouldRenderMultipleNodesInOrder() {
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("b")).build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            graph.addNode(nodeC);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            String text = view.toString();

            assertThat(text)
                    .isEqualTo(
                            """
                            ● a - Node A
                            │
                            ● b - Node B
                            │
                            ● c - Node C
                            """);
        }

        @Test
        @DisplayName("ノード ID と名前を含む")
        void shouldContainIdAndName() {
            MigrationNode nodeA = node("migration-001").name("Create users table").build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            String text = view.toString();

            assertThat(text).contains("migration-001");
            assertThat(text).contains("Create users table");
        }
    }

    @Nested
    @DisplayName("renderLines()")
    class RenderLinesTest {

        @Test
        @DisplayName("ラベル関数を各ノードに適用した結果を返す")
        void shouldApplyLabelFunctionToEachNode() {
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            List<String> lines = view.renderLines(n -> n.id().value() + ":" + n.name());

            assertThat(lines).containsExactly("● a:Node A", "│", "● b:Node B");
        }

        @Test
        @DisplayName("単一ノードで正しく動作する")
        void shouldWorkWithSingleNode() {
            MigrationNode nodeA = node("x").name("X Node").build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            List<String> lines = view.renderLines(n -> n.name());

            assertThat(lines).containsExactly("● X Node");
        }
    }

    @Nested
    @DisplayName("non-tree edge rendering")
    class NonTreeEdgeTest {

        @Test
        @DisplayName("ダイヤモンド DAG の非ツリーエッジを ┐ と ┘ で表現する")
        void shouldRenderNonTreeEdgesInDiamondDag() {
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD =
                    node("d").name("Node D").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            graph.addNode(nodeC);
            graph.addNode(nodeD);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            String text = view.toString();

            assertThat(text).contains("┐");
            assertThat(text).contains("┘");
        }
    }

    @Nested
    @DisplayName("reversed list constructor")
    class ReversedListConstructorTest {

        @Test
        @DisplayName("リスト外の依存を持つノードを reversed=true で渡しても例外にならず B を含む行を返す")
        void listConstructorReversed_singleNodeWithOutOfListDependency_doesNotThrow() {
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            ExecutionGraphView view = new ExecutionGraphView(List.of(nodeB), true);

            List<String> lines = view.renderLines(n -> n.id().value() + ":" + n.name());

            assertThat(lines).anyMatch(line -> line.contains("b:Node B"));
        }
    }

    @Nested
    @DisplayName("lines()")
    class LinesTest {

        @Test
        @DisplayName("ソート済みノードを NodeLineInfo(column=0) で返す")
        void shouldReturnNodeLineInfosWithColumnZero() {
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            List<NodeLineInfo> lines = view.lines();

            assertThat(lines).hasSize(2);
            assertThat(lines.get(0).node()).isEqualTo(nodeA);
            assertThat(lines.get(0).column()).isEqualTo(0);
            assertThat(lines.get(1).node()).isEqualTo(nodeB);
            assertThat(lines.get(1).column()).isEqualTo(0);
        }

        @Test
        @DisplayName("入力順序を保持する")
        void shouldPreserveInputOrder() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("b")).build();
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            graph.addNode(nodeC);
            ExecutionGraphView view = new ExecutionGraphView(graph, false);

            List<NodeLineInfo> lines = view.lines();

            assertThat(lines).extracting(NodeLineInfo::node).containsExactly(nodeA, nodeB, nodeC);
        }
    }
}
