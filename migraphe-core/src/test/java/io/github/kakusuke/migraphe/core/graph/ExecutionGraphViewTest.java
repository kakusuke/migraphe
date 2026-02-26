package io.github.kakusuke.migraphe.core.graph;

import static io.github.kakusuke.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import org.junit.jupiter.api.Disabled;
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
            MigrationNode nodeA = node("a").name("Create users table").build();
            List<MigrationNode> nodes = List.of(nodeA);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            String expected =
                    """
                    ● [ ] a - Create users table
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("toString() でプレーンテキストを取得できる")
        void shouldGetPlainTextFromToString() {
            MigrationNode nodeA = node("a").name("Create users table").build();
            List<MigrationNode> nodes = List.of(nodeA);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

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

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

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
    }

    @Nested
    @DisplayName("分岐・マージ")
    class BranchAndMerge {

        @Test
        @DisplayName("2分岐マージなし: A -> B, A -> C")
        void shouldRenderBranch() {
            // Given: A -> B, A -> C
            // Dom tree: A→{B, C}. Trunk = C (last in topo). B = branch.
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("a")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            // 支配木方式: B は branch (├─●)、C は trunk (●)
            String expected =
                    """
                    ● [ ] a - Node A
                    ├─● [ ] b - Node B
                    ● [ ] c - Node C
                    """;
            assertThat(text).isEqualTo(expected);
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

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);

            assertThat(view.lines()).hasSize(3);
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

            ExecutionGraphView view = new ExecutionGraphView(nodes, true);
            List<NodeLineInfo> lines = view.lines();

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
            // Dom tree: A→{B, C, D}. Trunk = D. B, C = branches.
            // Non-dom edges: B→D, C→D → レーン共通化
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD =
                    node("d").name("Node D").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            assertThat(view.lines()).hasSize(4);
            String expected =
                    """
                    ●     [ ] a - Node A
                    ├─●─┐ [ ] b - Node B
                    ├─●─┤ [ ] c - Node C
                    ├───┘
                    ●     [ ] d - Node D
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

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

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
            // Dom tree: A→{B, C, D}. Trunk = D (last in topo). B, C = branches.
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD = node("d").name("Node D").dependencies(NodeId.of("a")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            assertThat(view.lines()).hasSize(4);
            String expected =
                    """
                    ● [ ] a - Node A
                    ├─● [ ] b - Node B
                    ├─● [ ] c - Node C
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

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);

            assertThat(view.lines()).hasSize(4);
        }

        @Test
        @DisplayName("推移的依存: A->B->C, A->C (冗長な依存)")
        void shouldRenderTransitiveDependency() {
            // Given: C が A と B に依存、B が A に依存
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC =
                    node("c").name("Node C").dependencies(NodeId.of("a"), NodeId.of("b")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            // 冗長な依存を除去して1本の線で表示
            assertThat(view.lines()).hasSize(3);
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
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("b")).build();
            MigrationNode nodeD =
                    node("d").name("Node D").dependencies(NodeId.of("c"), NodeId.of("a")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            // 推移的簡約により直列表示
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
        @DisplayName("連続ダイヤモンド: A->B,C->D->E,F->G")
        void shouldRenderDoubleDiamond() {
            // Given: A -> B,C -> D -> E,F -> G
            // Non-dom edges: B→D, C→D, E→G, F→G
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

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            assertThat(view.lines()).hasSize(7);
            String expected =
                    """
                    ●     [ ] a - Node A
                    ├─●─┐ [ ] b - Node B
                    ├─●─┤ [ ] c - Node C
                    ├───┘
                    ●     [ ] d - Node D
                    ├─●─┐ [ ] e - Node E
                    ├─●─┤ [ ] f - Node F
                    ├───┘
                    ●     [ ] g - Node G
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("クロス依存: A,B -> C,D")
        void shouldRenderCrossDependency() {
            // Given: A -> C, A -> D, B -> C, B -> D
            // Dom tree (virtual root): →{A, B, C, D}. All children of virtual root.
            // Non-dom edges: A→C, A→D, B→C, B→D (all are non-dom since idom=VIRTUAL)
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").build();
            MigrationNode nodeC =
                    node("c").name("Node C").dependencies(NodeId.of("a"), NodeId.of("b")).build();
            MigrationNode nodeD =
                    node("d").name("Node D").dependencies(NodeId.of("a"), NodeId.of("b")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            assertThat(view.lines()).hasSize(4);
            assertThat(text).contains("[ ] a - Node A");
            assertThat(text).contains("[ ] b - Node B");
            assertThat(text).contains("[ ] c - Node C");
            assertThat(text).contains("[ ] d - Node D");
            // レーン描画を含む
            assertThat(text).contains("┐");
            assertThat(text).contains("┘");
        }

        @Test
        @DisplayName("複数ルート: A,B,C が独立したルート")
        void shouldRenderMultipleRoots() {
            // Given: A, B, C (すべて独立したルート)
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").build();
            MigrationNode nodeC = node("c").name("Node C").build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            assertThat(view.lines()).hasSize(3);
            String expected =
                    """
                    ● [ ] a - Node A
                    ● [ ] b - Node B
                    ● [ ] c - Node C
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("3分岐→マージ: A->B,C,D->E")
        void shouldRenderTripleBranchMerge() {
            // Given: A -> B, A -> C, A -> D, B -> E, C -> E, D -> E
            // Non-dom edges: B→E, C→E, D→E → 1レーンに共通化
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD = node("d").name("Node D").dependencies(NodeId.of("a")).build();
            MigrationNode nodeE =
                    node("e")
                            .name("Node E")
                            .dependencies(NodeId.of("b"), NodeId.of("c"), NodeId.of("d"))
                            .build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD, nodeE);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            assertThat(view.lines()).hasSize(5);
            String expected =
                    """
                    ●     [ ] a - Node A
                    ├─●─┐ [ ] b - Node B
                    ├─●─┤ [ ] c - Node C
                    ├─●─┤ [ ] d - Node D
                    ├───┘
                    ●     [ ] e - Node E
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("ブランチ内チェーン: A->B, A->C->D")
        void shouldRenderBranchWithChain() {
            // Given: A -> B, A -> C, C -> D
            // Dom tree: A→{B, C→D}. Trunk = C (deeper subtree). B = branch.
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD = node("d").name("Node D").dependencies(NodeId.of("c")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            assertThat(view.lines()).hasSize(4);
            String expected =
                    """
                    ● [ ] a - Node A
                    ├─● [ ] b - Node B
                    ● [ ] c - Node C
                    │
                    ● [ ] d - Node D
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("ブランチ内サブブランチ: A->{B,C}, B->{X,Y}, {B,C}->D")
        void shouldRenderSubBranchInBranch() {
            // Given: A -> B, A -> C, B -> X, B -> Y, B -> D, C -> D
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeX = node("x").name("Node X").dependencies(NodeId.of("b")).build();
            MigrationNode nodeY = node("y").name("Node Y").dependencies(NodeId.of("b")).build();
            MigrationNode nodeD =
                    node("d").name("Node D").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeX, nodeY, nodeD);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);

            assertThat(view.lines()).hasSize(6);
        }

        @Test
        @DisplayName("並列チェーン後マージ: A->B->C, D->E->F, {C,F}->G")
        void shouldRenderParallelChainsWithMerge() {
            // Given: A -> B -> C, D -> E -> F, C -> G, F -> G
            // Non-dom edges: C→G, F→G
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("b")).build();
            MigrationNode nodeD = node("d").name("Node D").build();
            MigrationNode nodeE = node("e").name("Node E").dependencies(NodeId.of("d")).build();
            MigrationNode nodeF = node("f").name("Node F").dependencies(NodeId.of("e")).build();
            MigrationNode nodeG =
                    node("g").name("Node G").dependencies(NodeId.of("c"), NodeId.of("f")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD, nodeE, nodeF, nodeG);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);

            assertThat(view.lines()).hasSize(7);
            String text = view.toString();
            assertThat(text).contains("[ ] g - Node G");
            // レーン描画を含む
            assertThat(text).contains("┐");
            assertThat(text).contains("┘");
        }

        @Test
        @DisplayName("深いネスト: A->B->C->D->E (5段チェーン)")
        void shouldRenderDeepChain() {
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("b")).build();
            MigrationNode nodeD = node("d").name("Node D").dependencies(NodeId.of("c")).build();
            MigrationNode nodeE = node("e").name("Node E").dependencies(NodeId.of("d")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD, nodeE);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            assertThat(view.lines()).hasSize(5);
            long connectorLines = text.lines().filter(l -> l.trim().equals("│")).count();
            assertThat(connectorLines).isEqualTo(4);
        }

        @Test
        @DisplayName("部分マージ: A->{B,C,D}, {B,C}->E (Dはマージしない)")
        void shouldRenderPartialMerge() {
            // Given: A -> B, A -> C, A -> D, B -> E, C -> E
            // Non-dom edges: B→E, C→E
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD = node("d").name("Node D").dependencies(NodeId.of("a")).build();
            MigrationNode nodeE =
                    node("e").name("Node E").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD, nodeE);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            assertThat(view.lines()).hasSize(5);
            String expected =
                    """
                    ●     [ ] a - Node A
                    ├─●─┐ [ ] b - Node B
                    ├─●─┤ [ ] c - Node C
                    ├─● │ [ ] d - Node D
                    ├───┘
                    ●     [ ] e - Node E
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("部分マージ（ノード順序がマージ後にDを配置）")
        void shouldRenderPartialMergeWithDAfterMerge() {
            // Given: 同じ構造、トポロジカル順序が異なる
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeE =
                    node("e").name("Node E").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            MigrationNode nodeD = node("d").name("Node D").dependencies(NodeId.of("a")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeE, nodeD);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            assertThat(view.lines()).hasSize(5);
            assertThat(text).contains("[ ] a - Node A");
            assertThat(text).contains("[ ] b - Node B");
            assertThat(text).contains("[ ] c - Node C");
            assertThat(text).contains("[ ] d - Node D");
            assertThat(text).contains("[ ] e - Node E");
        }

        @Test
        @DisplayName("サブグラフ分離: 001->{002,003,005}, {002,003}->004te, 003->004ed, 101独立")
        void shouldGroupSubgraphNodesTogether() {
            MigrationNode node001 = node("001").name("Create DB").build();
            MigrationNode node002 =
                    node("002").name("Create test").dependencies(NodeId.of("001")).build();
            MigrationNode node003 =
                    node("003").name("Create exam").dependencies(NodeId.of("001")).build();
            MigrationNode node004te =
                    node("004te")
                            .name("Test-Exam")
                            .dependencies(NodeId.of("002"), NodeId.of("003"))
                            .build();
            MigrationNode node004ed =
                    node("004ed").name("Exam-Detail").dependencies(NodeId.of("003")).build();
            MigrationNode node005 =
                    node("005").name("Create user").dependencies(NodeId.of("001")).build();
            MigrationNode node101 = node("101").name("Create testlog").build();

            List<MigrationNode> nodes =
                    List.of(node001, node003, node002, node005, node004te, node101, node004ed);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            assertThat(view.lines()).hasSize(7);
            assertThat(text).contains("[ ] 001 - Create DB");
            assertThat(text).contains("[ ] 101 - Create testlog");
        }

        @Test
        @DisplayName("複雑なマルチマージ: 4ルートが1ノードにマージ")
        void shouldRenderQuadMerge() {
            // Given: A, B, C, D -> E
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").build();
            MigrationNode nodeC = node("c").name("Node C").build();
            MigrationNode nodeD = node("d").name("Node D").build();
            MigrationNode nodeE =
                    node("e")
                            .name("Node E")
                            .dependencies(
                                    NodeId.of("a"), NodeId.of("b"), NodeId.of("c"), NodeId.of("d"))
                            .build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD, nodeE);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);

            assertThat(view.lines()).hasSize(5);
        }
    }

    @Nested
    @DisplayName("NodeLineInfo")
    class NodeLineInfoTest {

        @Test
        @DisplayName("lines() がノードと列情報を返す")
        void shouldReturnNodeAndColumn() {
            MigrationNode nodeA = node("a").name("Create users table").build();
            List<MigrationNode> nodes = List.of(nodeA);
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            NodeLineInfo lineInfo = view.lines().get(0);

            assertThat(lineInfo.node()).isEqualTo(nodeA);
            assertThat(lineInfo.column()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("支配木方式描画")
    class DominatorTreeRendering {

        @Test
        @DisplayName("フォーク（リーフのみ）: branch + trunk 表示")
        void forkLeafOnlyRendering() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("a")).build();
            ExecutionGraphView view = new ExecutionGraphView(List.of(nodeA, nodeB, nodeC), false);
            String text = view.toString();
            // A: trunk parent, B: branch (├─●), C: trunk (●)
            String expected =
                    """
                    ● [ ] a - A
                    ├─● [ ] b - B
                    ● [ ] c - C
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("ダイヤモンド: 支配木方式で分岐+マージ+レーン描画")
        void diamondRendering() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD =
                    node("d").name("D").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            ExecutionGraphView view =
                    new ExecutionGraphView(List.of(nodeA, nodeB, nodeC, nodeD), false);
            String text = view.toString();
            String expected =
                    """
                    ●     [ ] a - A
                    ├─●─┐ [ ] b - B
                    ├─●─┤ [ ] c - C
                    ├───┘
                    ●     [ ] d - D
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @Disabled("pre-existing Red: expected output not yet corrected")
        @DisplayName("ネストダイヤモンド: A→{B→{F,G}→H, C}→E — post-trunk + レーン描画")
        void nestedDiamondRendering() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeF = node("f").name("F").dependencies(NodeId.of("b")).build();
            MigrationNode nodeG = node("g").name("G").dependencies(NodeId.of("b")).build();
            MigrationNode nodeH =
                    node("h").name("H").dependencies(NodeId.of("f"), NodeId.of("g")).build();
            MigrationNode nodeE =
                    node("e").name("E").dependencies(NodeId.of("h"), NodeId.of("c")).build();

            ExecutionGraphView view =
                    new ExecutionGraphView(
                            List.of(nodeA, nodeB, nodeC, nodeF, nodeG, nodeH, nodeE), false);
            String text = view.toString();

            // E is post-trunk (receives non-dom edge H→E from trunk subtree)
            // C is pre-trunk
            // Lane 0: E group (C→E, H→E), Lane 1: H group (F→H, G→H)
            String expected =
                    """
                    ●      [ ] a - A
                    ├─●─┐  [ ] c - C
                    ●   │  [ ] b - B
                    ├─●─│┐ [ ] f - F
                    ├─●─│┤ [ ] g - G
                    ├───┼┘
                    ●───┤  [ ] h - H
                    ├───┘
                    └─●    [ ] e - E
                    """;
            assertThat(text).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("レーン割り当て")
    class LaneAssignment {

        @Test
        @DisplayName("単一ノードはレーン0")
        void singleNodeLane0() {
            MigrationNode nodeA = node("a").name("A").build();
            ExecutionGraphView view = new ExecutionGraphView(List.of(nodeA), false);
            assertThat(view.lines().get(0).column()).isEqualTo(0);
        }

        @Test
        @DisplayName("チェーンは同一レーン")
        void chainSameLane() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("b")).build();
            ExecutionGraphView view = new ExecutionGraphView(List.of(nodeA, nodeB, nodeC), false);
            assertThat(view.lines().get(0).column()).isEqualTo(0); // A
            assertThat(view.lines().get(1).column()).isEqualTo(0); // B
            assertThat(view.lines().get(2).column()).isEqualTo(0); // C
        }

        @Test
        @DisplayName("フォーク: branch はレーン1、trunk はレーン0")
        void forkLanes() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("a")).build();
            ExecutionGraphView view = new ExecutionGraphView(List.of(nodeA, nodeB, nodeC), false);
            // A: col 0 (root), B: col 1 (branch), C: col 0 (trunk)
            assertThat(view.lines().get(0).column()).isEqualTo(0); // A
            assertThat(view.lines().get(1).column()).isEqualTo(1); // B (branch)
            assertThat(view.lines().get(2).column()).isEqualTo(0); // C (trunk)
        }

        @Test
        @DisplayName("ダイヤモンド: branch はレーン1、マージ結果は trunk としてレーン0")
        void diamondLanes() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD =
                    node("d").name("D").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            ExecutionGraphView view =
                    new ExecutionGraphView(List.of(nodeA, nodeB, nodeC, nodeD), false);
            assertThat(view.lines().get(0).column()).isEqualTo(0); // A
            assertThat(view.lines().get(1).column()).isEqualTo(1); // B (branch)
            assertThat(view.lines().get(2).column()).isEqualTo(1); // C (branch)
            assertThat(view.lines().get(3).column()).isEqualTo(0); // D (trunk)
        }

        @Test
        @DisplayName("連続ダイヤモンド: trunk path はレーン0")
        void doubleDiamondLanes() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD =
                    node("d").name("D").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            MigrationNode nodeE = node("e").name("E").dependencies(NodeId.of("d")).build();
            MigrationNode nodeF = node("f").name("F").dependencies(NodeId.of("d")).build();
            MigrationNode nodeG =
                    node("g").name("G").dependencies(NodeId.of("e"), NodeId.of("f")).build();
            ExecutionGraphView view =
                    new ExecutionGraphView(
                            List.of(nodeA, nodeB, nodeC, nodeD, nodeE, nodeF, nodeG), false);
            // Trunk path: A→D→G (all col 0)
            assertThat(view.lines().get(0).column()).isEqualTo(0); // A
            assertThat(view.lines().get(1).column()).isEqualTo(1); // B (branch)
            assertThat(view.lines().get(2).column()).isEqualTo(1); // C (branch)
            assertThat(view.lines().get(3).column()).isEqualTo(0); // D (trunk)
            assertThat(view.lines().get(4).column()).isEqualTo(1); // E (branch)
            assertThat(view.lines().get(5).column()).isEqualTo(1); // F (branch)
            assertThat(view.lines().get(6).column()).isEqualTo(0); // G (trunk)
        }
    }

    @Nested
    @DisplayName("renderLines")
    class RenderLines {

        @Test
        @DisplayName("単一ノードで renderLines が正しい行リストを返す")
        void shouldRenderSingleNodeLines() {
            MigrationNode nodeA = node("a").name("Create table").build();
            ExecutionGraphView view = new ExecutionGraphView(List.of(nodeA), false);

            List<String> lines = view.renderLines(n -> "[OK] " + n.id().value() + " - " + n.name());

            assertThat(lines).hasSize(1);
            assertThat(lines.get(0)).contains("●").contains("[OK]").contains("a");
        }

        @Test
        @DisplayName("ダイヤモンド依存で正しい行数を返す（マージ行含む）")
        void shouldIncludeAllLineTypes() {
            // Given: A -> B, A -> C, B -> D, C -> D
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("Node C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD =
                    node("d").name("Node D").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            ExecutionGraphView view =
                    new ExecutionGraphView(List.of(nodeA, nodeB, nodeC, nodeD), false);

            List<String> lines = view.renderLines(n -> "[ ] " + n.id().value() + " - " + n.name());

            // 支配木方式: ノード行4 + マージ行1 = 5行
            assertThat(lines).hasSize(5);
            long nodeLines = lines.stream().filter(l -> l.contains("[ ]")).count();
            assertThat(nodeLines).isEqualTo(4);
        }

        @Test
        @DisplayName("labelFn がノードごとに異なるラベルを返せる")
        void shouldApplyDifferentStatusPerNode() {
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").dependencies(NodeId.of("a")).build();
            ExecutionGraphView view = new ExecutionGraphView(List.of(nodeA, nodeB), false);

            List<String> lines =
                    view.renderLines(
                            n -> {
                                String status = n.id().value().equals("a") ? "[✓]" : "[ ]";
                                return status + " " + n.id().value() + " - " + n.name();
                            });

            String allText = String.join("\n", lines);
            assertThat(allText).contains("[✓]");
            assertThat(allText).contains("[ ]");
        }
    }

    @Nested
    @DisplayName("非支配木辺レーン描画")
    class NonDomEdgeRendering {

        @Test
        @DisplayName("ダイヤモンドでレーン共通化: A→{B,C}→D — B→D, C→D を1レーンに共通化")
        void diamondLaneSharing() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD =
                    node("d").name("D").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            ExecutionGraphView view =
                    new ExecutionGraphView(List.of(nodeA, nodeB, nodeC, nodeD), false);
            String text = view.toString();

            // B→D と C→D が1つのレーンに共通化
            String expected =
                    """
                    ●     [ ] a - A
                    ├─●─┐ [ ] b - B
                    ├─●─┤ [ ] c - C
                    ├───┘
                    ●     [ ] d - D
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("連続ダイヤモンドで複数レーン: A→{B,C}→D→{E,F}→G — レーン再利用")
        void doubleDiamondMultipleLanes() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD =
                    node("d").name("D").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            MigrationNode nodeE = node("e").name("E").dependencies(NodeId.of("d")).build();
            MigrationNode nodeF = node("f").name("F").dependencies(NodeId.of("d")).build();
            MigrationNode nodeG =
                    node("g").name("G").dependencies(NodeId.of("e"), NodeId.of("f")).build();
            ExecutionGraphView view =
                    new ExecutionGraphView(
                            List.of(nodeA, nodeB, nodeC, nodeD, nodeE, nodeF, nodeG), false);
            String text = view.toString();

            // 2つのダイヤモンドが連続、レーン再利用
            String expected =
                    """
                    ●     [ ] a - A
                    ├─●─┐ [ ] b - B
                    ├─●─┤ [ ] c - C
                    ├───┘
                    ●     [ ] d - D
                    ├─●─┐ [ ] e - E
                    ├─●─┤ [ ] f - F
                    ├───┘
                    ●     [ ] g - G
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("ネストダイヤモンドで DFS 順序調整: post-trunk 描画")
        void nestedDiamondDfsReordering() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeF = node("f").name("F").dependencies(NodeId.of("b")).build();
            MigrationNode nodeG = node("g").name("G").dependencies(NodeId.of("b")).build();
            MigrationNode nodeH =
                    node("h").name("H").dependencies(NodeId.of("f"), NodeId.of("g")).build();
            MigrationNode nodeE =
                    node("e").name("E").dependencies(NodeId.of("h"), NodeId.of("c")).build();

            ExecutionGraphView view =
                    new ExecutionGraphView(
                            List.of(nodeA, nodeB, nodeC, nodeF, nodeG, nodeH, nodeE), false);

            // DFS 順序: C (pre-trunk), B subtree (trunk), E (post-trunk)
            assertThat(view.lines().get(0).node().id()).isEqualTo(NodeId.of("a"));
            assertThat(view.lines().get(1).node().id()).isEqualTo(NodeId.of("c")); // pre-trunk
            assertThat(view.lines().get(2).node().id()).isEqualTo(NodeId.of("b")); // trunk
            assertThat(view.lines().get(6).node().id()).isEqualTo(NodeId.of("e")); // post-trunk

            String text = view.toString();
            // E は └ で表示（最後の post-trunk branch）
            assertThat(text).contains("└─●");
        }

        @Test
        @DisplayName("レーン再利用: 中間行の縦線が消えない (laneRange 上書きバグ回帰)")
        void laneReuseKeepsIntermediateVerticalLines() {
            // Given: A→{B,C}→D where B→X→Y (Bの子チェーン), D→{P,Q}→R
            // 非支配木辺:
            //   Group1 (target D): B→D, C→D — lane 0, rows 1〜6 がアクティブ
            //   Group2 (target R): P→R, Q→R — lane 0 を再利用 (Group1 終了後)
            // バグ: laneRange[0] が Group2 の範囲で上書きされ、Group1 の中間行(X, Y行)で │ が消える
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeX = node("x").name("X").dependencies(NodeId.of("b")).build();
            MigrationNode nodeY = node("y").name("Y").dependencies(NodeId.of("x")).build();
            MigrationNode nodeD =
                    node("d").name("D").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            MigrationNode nodeP = node("p").name("P").dependencies(NodeId.of("d")).build();
            MigrationNode nodeQ = node("q").name("Q").dependencies(NodeId.of("d")).build();
            MigrationNode nodeR =
                    node("r").name("R").dependencies(NodeId.of("p"), NodeId.of("q")).build();

            List<MigrationNode> nodes =
                    List.of(nodeA, nodeB, nodeC, nodeX, nodeY, nodeD, nodeP, nodeQ, nodeR);
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            // X と Y の行: trunk chain の中間ノード。lane 0 がアクティブなので │ が見えるはず
            String xLine = text.lines().filter(l -> l.contains("[ ] x - X")).findFirst().orElse("");
            String yLine = text.lines().filter(l -> l.contains("[ ] y - Y")).findFirst().orElse("");
            assertThat(xLine).as("X行にレーン│が見えるはず").contains("│");
            assertThat(yLine).as("Y行にレーン│が見えるはず").contains("│");
        }

        @Test
        @DisplayName("非支配木辺なし: チェーンは変更なし")
        void noNonDomEdgesChain() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("b")).build();
            ExecutionGraphView view = new ExecutionGraphView(List.of(nodeA, nodeB, nodeC), false);
            String text = view.toString();

            // レーンなし
            String expected =
                    """
                    ● [ ] a - A
                    │
                    ● [ ] b - B
                    │
                    ● [ ] c - C
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("非支配木辺なし: 分岐のみは変更なし")
        void noNonDomEdgesForkOnly() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("a")).build();
            ExecutionGraphView view = new ExecutionGraphView(List.of(nodeA, nodeB, nodeC), false);
            String text = view.toString();

            // レーンなし
            String expected =
                    """
                    ● [ ] a - A
                    ├─● [ ] b - B
                    ● [ ] c - C
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("分岐ノードへの非支配木辺マージ行: ├ が col-1 (分岐コネクタ列) に配置される")
        void mergeRowForBranchNodePlacesJunctionAtConnectorColumn() {
            // A→{B(pre-trunk branch), C(trunk)→D}, {B,D}→E(post-trunk branch of A)
            // idom(E)=A, non-dom edges: B→E, D→E
            // E is at col=1 (branch), merge row ├ must be at col-1=0
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD = node("d").name("D").dependencies(NodeId.of("c")).build();
            MigrationNode nodeE =
                    node("e").name("E").dependencies(NodeId.of("b"), NodeId.of("d")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD, nodeE);
            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();
            String expected =
                    """
                    ●     [ ] a - A
                    ├─●─┐ [ ] b - B
                    ●   │ [ ] c - C
                    │   │
                    ●───┤ [ ] d - D
                    ├───┘
                    └─●   [ ] e - E
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("マージ行: ターゲットノードが開くレーンを誤表示しない")
        void mergeRowDoesNotShowLanesOpenedByMergeTarget() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD =
                    node("d").name("D").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            MigrationNode nodeF = node("f").name("F").dependencies(NodeId.of("a")).build();
            MigrationNode nodeE =
                    node("e").name("E").dependencies(NodeId.of("d"), NodeId.of("f")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD, nodeF, nodeE);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            String expected =
                    """
                    ●      [ ] a - A
                    ├─●─┐  [ ] b - B
                    ├─●─┤  [ ] c - C
                    ├───┘
                    ├─●──┐ [ ] d - D
                    ├─●──┤ [ ] f - F
                    ├────┘
                    ●      [ ] e - E
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("└ vs ├: post-trunk branch の最後が └ になる")
        void lastPostTrunkBranchUsesElbow() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeF = node("f").name("F").dependencies(NodeId.of("b")).build();
            MigrationNode nodeG = node("g").name("G").dependencies(NodeId.of("b")).build();
            MigrationNode nodeH =
                    node("h").name("H").dependencies(NodeId.of("f"), NodeId.of("g")).build();
            MigrationNode nodeE =
                    node("e").name("E").dependencies(NodeId.of("h"), NodeId.of("c")).build();

            ExecutionGraphView view =
                    new ExecutionGraphView(
                            List.of(nodeA, nodeB, nodeC, nodeF, nodeG, nodeH, nodeE), false);
            String text = view.toString();

            // E は post-trunk の最後 → └─● で表示
            assertThat(text).contains("└─●");
            // C は pre-trunk → ├─● で表示
            assertThat(text).contains("├─●─┐");
        }

        @Test
        @Disabled("pre-existing Red: not yet implemented")
        @DisplayName("post-trunk サブツリーから non-dom edge が来るブランチも post-trunk に分類される")
        void reversedNonDomEdgeGroupProducesNoSpuriousMergeRow() {
            // CA → TCx → TCA (trunk chain)
            // B_other, B_other2: pre-trunk branches of CA
            // S.deps = {B_other, TCA}: post-trunk (TCA→S is non-dom)
            // X.deps = {B_other2, TCA}: post-trunk (TCA→X is non-dom)
            // T.deps = {S, X}: S と X が全て post-trunk なので T も post-trunk に分類される
            MigrationNode nodeCa = node("ca").name("CA").build();
            MigrationNode nodeTCx = node("tcx").name("TCx").dependencies(NodeId.of("ca")).build();
            MigrationNode nodeTCA = node("tca").name("TCA").dependencies(NodeId.of("tcx")).build();
            MigrationNode nodeBOther =
                    node("b_other").name("B_other").dependencies(NodeId.of("ca")).build();
            MigrationNode nodeBOther2 =
                    node("b_other2").name("B_other2").dependencies(NodeId.of("ca")).build();
            MigrationNode nodeS =
                    node("s")
                            .name("S")
                            .dependencies(NodeId.of("b_other"), NodeId.of("tca"))
                            .build();
            MigrationNode nodeX =
                    node("x")
                            .name("X")
                            .dependencies(NodeId.of("b_other2"), NodeId.of("tca"))
                            .build();
            MigrationNode nodeT =
                    node("t").name("T").dependencies(NodeId.of("s"), NodeId.of("x")).build();

            ExecutionGraphView view =
                    new ExecutionGraphView(
                            List.of(
                                    nodeCa,
                                    nodeTCx,
                                    nodeTCA,
                                    nodeBOther,
                                    nodeBOther2,
                                    nodeS,
                                    nodeX,
                                    nodeT),
                            false);

            // T は S・X より後に描画される（post-trunk に正しく分類される）
            List<String> nodeOrder = view.lines().stream().map(l -> l.node().id().value()).toList();
            int tIdx = nodeOrder.indexOf("t");
            int sIdx = nodeOrder.indexOf("s");
            int xIdx = nodeOrder.indexOf("x");
            assertThat(tIdx).as("T は S より後に描画される").isGreaterThan(sIdx);
            assertThat(tIdx).as("T は X より後に描画される").isGreaterThan(xIdx);
        }
    }

    @Nested
    @DisplayName("removeTransitiveParents バグ修正")
    class RemoveTransitiveParentsBugFix {

        @Test
        @DisplayName("orders を共通依存に持つ複数チェーンを集約するノードが、payments と shipments より後に表示される")
        void orderIndexesAppearsAfterPaymentsAndShipments() {
            // sample/ 003_order_indexes の問題を再現する最小グラフ
            // 実際の sample 構造を再現:
            //   grant_permissions (root)
            //     ├─ currencies
            //     ├─ locales
            //     ├─ payment_providers
            //     └─ carriers
            //   users (currencies, locales)
            //   addresses (users)
            //   payment_methods (payment_providers, users)
            //   orders (users, addresses, currencies)  ← carriers に依存しない
            //   order_items (orders)
            //   payments (orders, payment_methods)
            //   shipments (orders, carriers)           ← orders も carriers も idom=grant
            //   order_indexes (orders, order_items, payments, shipments)
            //
            // 問題: payments.idom = grant (orders と payment_methods の LCA)
            //        shipments.idom = grant (orders と carriers の LCA)
            //        → order_indexes.idom = grant → payments/shipments より前に描画されてしまう

            MigrationNode grant = node("grant").name("grant_permissions").build();
            MigrationNode currencies =
                    node("currencies").name("currencies").dependencies(NodeId.of("grant")).build();
            MigrationNode locales =
                    node("locales").name("locales").dependencies(NodeId.of("grant")).build();
            MigrationNode payProviders =
                    node("pay_providers")
                            .name("payment_providers")
                            .dependencies(NodeId.of("grant"))
                            .build();
            MigrationNode carriers =
                    node("carriers").name("carriers").dependencies(NodeId.of("grant")).build();
            MigrationNode users =
                    node("users")
                            .name("users")
                            .dependencies(NodeId.of("currencies"), NodeId.of("locales"))
                            .build();
            MigrationNode addresses =
                    node("addresses").name("addresses").dependencies(NodeId.of("users")).build();
            MigrationNode payMethods =
                    node("pay_methods")
                            .name("payment_methods")
                            .dependencies(NodeId.of("pay_providers"), NodeId.of("users"))
                            .build();
            MigrationNode orders =
                    node("orders")
                            .name("orders")
                            .dependencies(
                                    NodeId.of("users"),
                                    NodeId.of("addresses"),
                                    NodeId.of("currencies"))
                            .build();
            MigrationNode orderItems =
                    node("order_items")
                            .name("order_items")
                            .dependencies(NodeId.of("orders"))
                            .build();
            MigrationNode payments =
                    node("payments")
                            .name("payments")
                            .dependencies(NodeId.of("orders"), NodeId.of("pay_methods"))
                            .build();
            MigrationNode shipments =
                    node("shipments")
                            .name("shipments")
                            .dependencies(NodeId.of("orders"), NodeId.of("carriers"))
                            .build();
            MigrationNode orderIndexes =
                    node("order_indexes")
                            .name("order_indexes")
                            .dependencies(
                                    NodeId.of("orders"),
                                    NodeId.of("order_items"),
                                    NodeId.of("payments"),
                                    NodeId.of("shipments"))
                            .build();

            // NOTE: orderIndexes を payments/shipments より前に渡す（実際の sample の DFS 順序を再現）
            // 実際の sample では 17_indexes/* が topoSort で payments/shipments より前に来ることがある
            List<MigrationNode> nodes =
                    List.of(
                            grant,
                            currencies,
                            locales,
                            payProviders,
                            carriers,
                            users,
                            addresses,
                            payMethods,
                            orders,
                            orderItems,
                            orderIndexes,
                            payments,
                            shipments);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            List<String> nodeOrder = view.lines().stream().map(l -> l.node().id().value()).toList();

            int paymentsIdx = nodeOrder.indexOf("payments");
            int shipmentsIdx = nodeOrder.indexOf("shipments");
            int orderIndexesIdx = nodeOrder.indexOf("order_indexes");

            assertThat(orderIndexesIdx)
                    .as("order_indexes は payments より後に描画される")
                    .isGreaterThan(paymentsIdx);
            assertThat(orderIndexesIdx)
                    .as("order_indexes は shipments より後に描画される")
                    .isGreaterThan(shipmentsIdx);
        }

        @Test
        @DisplayName(
                "removeTransitiveParents が orders を除去した結果 order_indexes の idom が不当に上がり、payments"
                        + " より前に表示されるバグ")
        void orderIndexesAppearsAfterPaymentsWhenOrdersIsTransitivelyRemoved() {
            // 最小再現グラフ (sample/ 003_order_indexes の問題を最小化):
            //   G (root, grant_permissions 相当)
            //   O1 = orders: deps={G}
            //   O2 = order_items: deps={O1}       ← O1 の子
            //   B  = other_thing: deps={G}         ← payment_methods の親相当 (O1 と独立)
            //   P  = payments: deps={O1, B}        ← O1 と B の両方に依存
            //   T  = order_indexes: deps={O1, O2, P}
            //
            // removeTransitiveParents(T.deps = {O1, O2, P}):
            //   canReachDown(O1, O2) = true (O1→O2 in childrenOf) → O1 除去
            //   parentsOf(T) = {O2, P}
            //   idom(P) = LCA(O1, B) = G (O1 と B は G の子同士で共通祖先は G のみ)
            //   idom(T) = LCA(O2, P): O2の祖先={O1,G}, P の idom=G → LCA=G
            //   → T.idom = G → T が DOM tree で G の直下に配置
            //   → DFS で T が P (payments) より前に描画されてしまう
            MigrationNode nodeG = node("g").name("G").build();
            MigrationNode nodeO1 = node("o1").name("orders").dependencies(NodeId.of("g")).build();
            MigrationNode nodeO2 =
                    node("o2").name("order_items").dependencies(NodeId.of("o1")).build();
            MigrationNode nodeB =
                    node("b").name("other_thing").dependencies(NodeId.of("g")).build();
            MigrationNode nodeP =
                    node("p")
                            .name("payments")
                            .dependencies(NodeId.of("o1"), NodeId.of("b"))
                            .build();
            MigrationNode nodeT =
                    node("t")
                            .name("order_indexes")
                            .dependencies(NodeId.of("o1"), NodeId.of("o2"), NodeId.of("p"))
                            .build();

            // T を P の後に渡す（topoSort 的に正しい順序）
            // この順序の場合、idom[p] が計算済みになった後に T の idom を計算する
            // → idom(T) = LCA(o2, p) で p の idom = g が使われ → idom(T) = g (上位すぎる)
            List<MigrationNode> nodes = List.of(nodeG, nodeO1, nodeO2, nodeB, nodeP, nodeT);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            System.err.println("=== DEBUG orderIndexesAppearsAfterPayments (P before T) ===");
            System.err.println(view.toString());
            List<String> nodeOrder = view.lines().stream().map(l -> l.node().id().value()).toList();
            System.err.println("nodeOrder: " + nodeOrder);

            int o2Idx = nodeOrder.indexOf("o2");
            int pIdx = nodeOrder.indexOf("p");
            int tIdx = nodeOrder.indexOf("t");

            assertThat(tIdx)
                    .as("T (order_indexes) は O2 (order_items) より後に描画される")
                    .isGreaterThan(o2Idx);
            assertThat(tIdx).as("T (order_indexes) は P (payments) より後に描画される").isGreaterThan(pIdx);
        }
    }
}
