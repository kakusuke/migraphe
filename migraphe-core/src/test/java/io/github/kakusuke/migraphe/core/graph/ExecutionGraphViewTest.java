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
            // Dom tree: A→{B, C, D}. Trunk = D (last in topo). B, C = branches.
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
                    ● [ ] a - Node A
                    ├─● [ ] b - Node B
                    ├─● [ ] c - Node C
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
            // Dom tree (virtual root): →{A, B, C, D}. All children of virtual root.
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
            // Dom tree: A→{B, C, D→{E, F, G}}. Trunk path: A→D→G.
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
                    ● [ ] a - Node A
                    ├─● [ ] b - Node B
                    ├─● [ ] c - Node C
                    ● [ ] d - Node D
                    ├─● [ ] e - Node E
                    ├─● [ ] f - Node F
                    ● [ ] g - Node G
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("クロス依存: A,B -> C,D")
        void shouldRenderCrossDependency() {
            // Given: A -> C, A -> D, B -> C, B -> D
            // Dom tree (virtual root): →{A, B, C, D}. All children of virtual root.
            MigrationNode nodeA = node("a").name("Node A").build();
            MigrationNode nodeB = node("b").name("Node B").build();
            MigrationNode nodeC =
                    node("c").name("Node C").dependencies(NodeId.of("a"), NodeId.of("b")).build();
            MigrationNode nodeD =
                    node("d").name("Node D").dependencies(NodeId.of("a"), NodeId.of("b")).build();
            List<MigrationNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD);

            ExecutionGraphView view = new ExecutionGraphView(nodes, false);
            String text = view.toString();

            // 支配木方式: 全て仮想ルートの子 → 独立ルート扱い
            assertThat(view.lines()).hasSize(4);
            assertThat(text).contains("● [ ] a - Node A");
            assertThat(text).contains("● [ ] b - Node B");
            assertThat(text).contains("● [ ] c - Node C");
            assertThat(text).contains("● [ ] d - Node D");
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
            // Dom tree: A→{B, C, D, E}. Trunk = E (last in topo). B, C, D = branches.
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
                    ● [ ] a - Node A
                    ├─● [ ] b - Node B
                    ├─● [ ] c - Node C
                    ├─● [ ] d - Node D
                    ● [ ] e - Node E
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
            // Dom tree: A→{B→{X, Y}, C, D}. idom(D) = A (LCA of B and C = A).
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
            // Dom tree (virtual root): →{A→B→C, D→E→F, G}
            // idom(G) = virtual root (LCA of C and F in dom tree)
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
            assertThat(text).contains("● [ ] g - Node G");
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
            // Dom tree: A→{B, C, D, E}. idom(E) = A (LCA of B and C = A).
            // Trunk = E (last in topo among same-depth children).
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
                    ● [ ] a - Node A
                    ├─● [ ] b - Node B
                    ├─● [ ] c - Node C
                    ├─● [ ] d - Node D
                    ● [ ] e - Node E
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

            // 支配木方式: 全て A の dom children。E は branch として表示される。
            assertThat(view.lines()).hasSize(5);
            assertThat(text).contains("● [ ] a - Node A");
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

            // 支配木方式: 仮想ルート→{101, 001→{...}}
            // 001 が trunk (深いサブツリー)、101 が branch (先に描画)
            assertThat(view.lines()).hasSize(7);
            assertThat(text).contains("● [ ] 001 - Create DB");
            assertThat(text).contains("● [ ] 101 - Create testlog");
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
        @DisplayName("ダイヤモンド: 支配木方式で分岐+マージ")
        void diamondRendering() {
            MigrationNode nodeA = node("a").name("A").build();
            MigrationNode nodeB = node("b").name("B").dependencies(NodeId.of("a")).build();
            MigrationNode nodeC = node("c").name("C").dependencies(NodeId.of("a")).build();
            MigrationNode nodeD =
                    node("d").name("D").dependencies(NodeId.of("b"), NodeId.of("c")).build();
            ExecutionGraphView view =
                    new ExecutionGraphView(List.of(nodeA, nodeB, nodeC, nodeD), false);
            String text = view.toString();
            // Dom tree: A→{B, C, D}. Trunk = D. B, C = branches.
            String expected =
                    """
                    ● [ ] a - A
                    ├─● [ ] b - B
                    ├─● [ ] c - C
                    ● [ ] d - D
                    """;
            assertThat(text).isEqualTo(expected);
        }

        @Test
        @DisplayName("ネストダイヤモンド: A→{B→{F,G}→H, C}→E")
        void nestedDiamondRendering() {
            // A → B, A → C, B → F, B → G, F → H, G → H, B → E, C → E
            // Actually: A→{B, C}, B→{F, G}, {F,G}→H, {H, C}→E
            // But simpler: A→B, A→C, B→F, B→G, F→H, G→H, H→E, C→E
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

            // Dom tree: A→{B→{F, G, H}, C, E}. Trunk = B (deepest subtree).
            // Branches of A: C, E. Then trunk B with its subtree.
            String expected =
                    """
                    ● [ ] a - A
                    ├─● [ ] c - C
                    ├─● [ ] e - E
                    ● [ ] b - B
                    ├─● [ ] f - F
                    ├─● [ ] g - G
                    ● [ ] h - H
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
        @DisplayName("ダイヤモンド依存で正しい行数を返す")
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

            // 支配木方式: ノード行4 のみ（コネクタ行なし、branch が間に入るため）
            assertThat(lines).hasSize(4);
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
}
