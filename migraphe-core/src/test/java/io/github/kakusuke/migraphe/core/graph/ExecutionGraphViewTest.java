package io.github.kakusuke.migraphe.core.graph;

import static io.github.kakusuke.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExecutionGraphView")
class ExecutionGraphViewTest {

    @Nested
    @DisplayName("単一ノード")
    class SingleNode {

        @Test
        @DisplayName("単一ノードのグラフを表示できる")
        void shouldRenderSingleNode() {
            // Given
            MigrationNode nodeA = node("a").name("Create users table").build();
            List<MigrationNode> nodes = List.of(nodeA);

            // When
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            List<NodeLineInfo> lines = view.lines();

            // Then
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0).node()).isEqualTo(nodeA);
            assertThat(lines.get(0).graphPrefix()).contains("●");
        }

        @Test
        @DisplayName("toString() でプレーンテキストを取得できる")
        void shouldGetPlainTextFromToString() {
            // Given
            MigrationNode nodeA = node("a").name("Create users table").build();
            List<MigrationNode> nodes = List.of(nodeA);

            // When
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            // Then
            assertThat(text).contains("●");
            assertThat(text).contains("a");
            assertThat(text).contains("Create users table");
        }
    }

    @Nested
    @DisplayName("直列依存")
    class LinearDependency {

        @Test
        @DisplayName("直列依存のグラフを表示できる")
        void shouldRenderLinearDependency() {
            // Given: A -> B -> C
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("b")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC);

            // When
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            List<NodeLineInfo> lines = view.lines();

            // Then
            assertThat(lines).hasSize(3);
            // 接続線が存在する
            assertThat(view.toString()).contains("│");
        }
    }

    @Nested
    @DisplayName("分岐・マージ")
    class BranchAndMerge {

        @Test
        @DisplayName("分岐するグラフを表示できる")
        void shouldRenderBranch() {
            // Given: A -> B, A -> C
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("a")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC);

            // When
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            List<NodeLineInfo> lines = view.lines();

            // Then
            assertThat(lines).hasSize(3);
            // 分岐線が存在する
            String text = view.toString();
            assertThat(text).containsAnyOf("├", "┬", "┐");
        }

        @Test
        @DisplayName("マージするグラフを表示できる")
        void shouldRenderMerge() {
            // Given: A -> C, B -> C
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").build();
            MigrationNode nodeC =
                    node("c").name("Node C").dependencies(NodeId.of("a"), NodeId.of("b")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC);

            // When
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            List<NodeLineInfo> lines = view.lines();

            // Then
            assertThat(lines).hasSize(3);
            // マージ線が存在する可能性
            String text = view.toString();
            assertThat(text).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("逆順モード")
    class ReversedMode {

        @Test
        @DisplayName("逆順モードでグラフを表示できる")
        void shouldRenderReversedGraph() {
            // Given: A -> B (DOWN 用は逆順)
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            List<MigrationNode> nodes = List.of(nodeB, nodeA); // 逆順で渡す

            // When
            ExecutionGraphView view = new ExecutionGraphView(nodes, true);
            List<NodeLineInfo> lines = view.lines();

            // Then
            assertThat(lines).hasSize(2);
            assertThat(lines.get(0).node()).isEqualTo(nodeB);
            assertThat(lines.get(1).node()).isEqualTo(nodeA);
        }
    }

    @Nested
    @DisplayName("NodeLineInfo")
    class NodeLineInfoTest {

        @Test
        @DisplayName("toPlainText() でステータス付きテキストを取得できる")
        void shouldGetPlainTextWithStatus() {
            // Given
            MigrationNode nodeA = node("a").name("Create users table").build();
            List<MigrationNode> nodes = List.of(nodeA);
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            NodeLineInfo lineInfo = view.lines().get(0);

            // When
            String plainText = lineInfo.toPlainText("[OK]");

            // Then
            assertThat(plainText).contains("●");
            assertThat(plainText).contains("[OK]");
            assertThat(plainText).contains("a");
            assertThat(plainText).contains("Create users table");
        }
    }
}
