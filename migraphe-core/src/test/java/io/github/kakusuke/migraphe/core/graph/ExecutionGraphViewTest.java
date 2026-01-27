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
    @DisplayName("複雑な依存関係")
    class ComplexDependencies {

        @Test
        @DisplayName("ダイヤモンド依存: A -> B,C -> D")
        void shouldRenderDiamond() {
            // Given: A -> B, A -> C, B -> D, C -> D
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD =
                    node("d").name("Node D").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD);

            // When
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            // Then
            assertThat(view.lines()).hasSize(4);
            // 期待出力（スナップショット）
            // 注: C の後のコネクタラインは D がマージするまでアクティブ
            String expected =
                    """
                    ● [ ] a - Node A
                    ├─┐
                    │ │
                    ● │ [ ] b - Node B
                    │ │
                    │ ● [ ] c - Node C
                    │ │
                    ├─┘
                    ● [ ] d - Node D
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("並列独立チェーン: A->B, C->D")
        void shouldRenderParallelChains() {
            // Given: A -> B, C -> D (互いに独立)
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").build();
            MigrationNode nodeD = node("d").name("Node D").dependencies(NodeId.of("c")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD);

            // When
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            // Then - 独立チェーンは別々の列に表示
            assertThat(view.lines()).hasSize(4);
            String expected =
                    """
                    ● [ ] a - Node A
                    │
                    ● [ ] b - Node B
                    ● [ ] c - Node C
                    │
                    ● [ ] d - Node D
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("3分岐: A -> B,C,D")
        void shouldRenderTripleBranch() {
            // Given: A -> B, A -> C, A -> D
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD = node("d").name("Node D").dependencies(NodeId.of("a")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD);

            // When
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            // Then - 3分岐（終端ノードは処理後にカラム解放）
            assertThat(view.lines()).hasSize(4);
            String expected =
                    """
                    ● [ ] a - Node A
                    ├─┬─┐
                    │ │ │
                    ● │ │ [ ] b - Node B
                      │ │
                      ● │ [ ] c - Node C
                        │
                        ● [ ] d - Node D
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("3マージ: A,B,C -> D")
        void shouldRenderTripleMerge() {
            // Given: A -> D, B -> D, C -> D
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").build();
            MigrationNode nodeC = node("c").name("Node C").build();
            MigrationNode nodeD =
                    node("d")
                            .name("Node D")
                            .dependencies(NodeId.of("a"), NodeId.of("b"), NodeId.of("c"))
                            .build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD);

            // When
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            // Then
            System.out.println("=== 3マージ ===");
            System.out.println(text);
            System.out.println("===============");

            assertThat(view.lines()).hasSize(4);
        }

        @Test
        @DisplayName("推移的依存: A->B->C, A->C (冗長な依存)")
        void shouldRenderTransitiveDependency() {
            // Given: C が A と B に依存、B が A に依存
            // A -> B -> C
            // └───────┘ (A->C は冗長)
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC =
                    node("c").name("Node C").dependencies(NodeId.of("a"), NodeId.of("b")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC);

            // When
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            // Then - 冗長な依存を除去して1本の線で表示
            System.out.println("=== 推移的依存 ===");
            System.out.println(text);
            System.out.println("==================");

            assertThat(view.lines()).hasSize(3);
            // 期待: 分岐せずに直列表示
            String expected =
                    """
                    ● [ ] a - Node A
                    │
                    ● [ ] b - Node B
                    │
                    ● [ ] c - Node C
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("非対称分岐マージ: A->B->C->D, A->D (推移的簡約で1本)")
        void shouldRenderAsymmetricBranchMerge() {
            // Given: A -> B -> C -> D, A -> D
            // A -> D は A -> B -> C -> D を通じて満たされるので冗長
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("b")).build();
            MigrationNode nodeD =
                    node("d").name("Node D").dependencies(NodeId.of("c"), NodeId.of("a")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD);

            // When
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            // Then - 推移的簡約により直列表示
            assertThat(view.lines()).hasSize(4);
            String expected =
                    """
                    ● [ ] a - Node A
                    │
                    ● [ ] b - Node B
                    │
                    ● [ ] c - Node C
                    │
                    ● [ ] d - Node D
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("連続ダイヤモンド")
        void shouldRenderDoubleDiamond() {
            // Given: A -> B,C -> D -> E,F -> G
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD =
                    node("d").name("Node D").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            MigrationNode nodeE = node("e").name("Node E").dependencies(NodeId.of("d")).build();
            MigrationNode nodeF = node("f").name("Node F").dependencies(NodeId.of("d")).build();
            MigrationNode nodeG =
                    node("g").name("Node G").dependencies(NodeId.of("e"), NodeId.of("f")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD, nodeE, nodeF, nodeG);

            // When
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            // Then
            System.out.println("=== 連続ダイヤモンド ===");
            System.out.println(text);
            System.out.println("=======================");

            assertThat(view.lines()).hasSize(7);
        }

        @Test
        @DisplayName("クロス依存: A,B -> C,D")
        void shouldRenderCrossDependency() {
            // Given: A -> C, A -> D, B -> C, B -> D
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").build();
            MigrationNode nodeC =
                    node("c").name("Node C").dependencies(NodeId.of("a"), NodeId.of("b")).build();
            MigrationNode nodeD =
                    node("d").name("Node D").dependencies(NodeId.of("a"), NodeId.of("b")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD);

            // When
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            // Then - クロス依存: A と B がそれぞれ分岐
            assertThat(view.lines()).hasSize(4);
            String expected =
                    """
                    ● [ ] a - Node A
                    ├─┐
                    │ │
                    │ │ ● [ ] b - Node B
                    │ │ ├─┐
                    │ │ │ │
                    ├───┘ │
                    ● │   │ [ ] c - Node C
                      │   │
                      ├───┘
                      ● [ ] d - Node D
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("複数ルート: A,B,C が独立したルート")
        void shouldRenderMultipleRoots() {
            // Given: A, B, C (すべて独立したルート)
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").build();
            MigrationNode nodeC = node("c").name("Node C").build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC);

            // When
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            // Then
            System.out.println("=== 複数ルート ===");
            System.out.println(text);
            System.out.println("=================");

            assertThat(view.lines()).hasSize(3);
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
