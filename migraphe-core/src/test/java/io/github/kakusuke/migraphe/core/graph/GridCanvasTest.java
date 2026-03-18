package io.github.kakusuke.migraphe.core.graph;

import static io.github.kakusuke.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("GridCanvas")
class GridCanvasTest {

    @Nested
    @DisplayName("addStream()")
    class AddStreamTest {

        @Test
        @DisplayName("単一ノードのストリームは '● a' の1行を描画する")
        void shouldRenderSingleNodeStream() {
            MigrationNode nodeA = node("a").build();
            LayoutStream stream = new LayoutStream(null, List.of(nodeA), List.of());

            GridCanvas canvas = new GridCanvas();
            canvas.addStream(stream);

            assertThat(canvas.render(n -> n.id().value()))
                    .isEqualTo("""
                            ● a
                            """);
        }

        @Test
        @DisplayName("3ノードの直線ストリームは縦線で繋がった3行を描画する")
        void shouldRenderLinearChainWithVerticals() {
            MigrationNode nodeA = node("a").build();
            MigrationNode nodeB = node("b").build();
            MigrationNode nodeC = node("c").build();
            LayoutStream stream = new LayoutStream(null, List.of(nodeA, nodeB, nodeC), List.of());

            GridCanvas canvas = new GridCanvas();
            canvas.addStream(stream);

            assertThat(canvas.render(n -> n.id().value()))
                    .isEqualTo(
                            """
                            ● a
                            │
                            ● b
                            │
                            ● c
                            """);
        }

        @Test
        @DisplayName("単一ノードの子ストリームは forkNode 直後に分岐して描画する")
        void shouldRenderForkWithSingleChildNode() {
            MigrationNode nodeA = node("a").build();
            MigrationNode nodeB = node("b").build();
            MigrationNode nodeC = node("c").build();
            LayoutStream childStream = new LayoutStream(NodeId.of("a"), List.of(nodeC), List.of());
            LayoutStream rootStream =
                    new LayoutStream(null, List.of(nodeA, nodeB), List.of(childStream));

            GridCanvas canvas = new GridCanvas();
            canvas.addStream(rootStream);

            assertThat(canvas.render(n -> n.id().value()))
                    .isEqualTo(
                            """
                            ● a
                            ├● c
                            │
                            ● b
                            """);
        }

        @Test
        @DisplayName("複数ノードの子ストリームは親列の縦線を継続しながら描画する")
        void shouldRenderForkWithMultiNodeChildStream() {
            MigrationNode nodeA = node("a").build();
            MigrationNode nodeB = node("b").build();
            MigrationNode nodeC = node("c").build();
            MigrationNode nodeD = node("d").build();
            LayoutStream childStream =
                    new LayoutStream(NodeId.of("a"), List.of(nodeC, nodeD), List.of());
            LayoutStream rootStream =
                    new LayoutStream(null, List.of(nodeA, nodeB), List.of(childStream));

            GridCanvas canvas = new GridCanvas();
            canvas.addStream(rootStream);

            assertThat(canvas.render(n -> n.id().value()))
                    .isEqualTo(
                            """
                            ● a
                            ├● c
                            ││
                            │● d
                            │
                            ● b
                            """);
        }

        @Test
        @DisplayName("異なる forkNode からそれぞれ子ストリームが分岐する")
        void shouldRenderForksFromDifferentParentNodes() {
            MigrationNode nodeA = node("a").build();
            MigrationNode nodeB = node("b").build();
            MigrationNode nodeC = node("c").build();
            MigrationNode nodeD = node("d").build();
            MigrationNode nodeE = node("e").build();
            LayoutStream childD = new LayoutStream(NodeId.of("a"), List.of(nodeD), List.of());
            LayoutStream childE = new LayoutStream(NodeId.of("b"), List.of(nodeE), List.of());
            LayoutStream rootStream =
                    new LayoutStream(null, List.of(nodeA, nodeB, nodeC), List.of(childD, childE));

            GridCanvas canvas = new GridCanvas();
            canvas.addStream(rootStream);

            assertThat(canvas.render(n -> n.id().value()))
                    .isEqualTo(
                            """
                            ● a
                            ├● d
                            │
                            ● b
                            ├● e
                            │
                            ● c
                            """);
        }
    }

    @Nested
    @DisplayName("addNonTreeEdge()")
    class AddNonTreeEdgeTest {

        @Test
        @DisplayName("ダイヤモンド DAG の非ツリー辺はマージ行を挿入して正しく描画する")
        void shouldRenderDiamondDagWithMergeRow() {
            MigrationNode nodeA = node("a").build();
            MigrationNode nodeB = node("b").build();
            MigrationNode nodeC = node("c").build();
            MigrationNode nodeD = node("d").build();
            LayoutStream childStream = new LayoutStream(NodeId.of("a"), List.of(nodeB), List.of());
            LayoutStream rootStream =
                    new LayoutStream(null, List.of(nodeA, nodeC, nodeD), List.of(childStream));

            GridCanvas canvas = new GridCanvas();
            canvas.addStream(rootStream);
            canvas.addNonTreeEdge(NodeId.of("b"), NodeId.of("d"));

            assertThat(canvas.render(n -> n.id().value()))
                    .isEqualTo(
                            """
                            ●   a
                            ├●┐ b
                            │ │
                            ● │ c
                            │ │
                            ├─┘
                            ●   d
                            """);
        }

        @Test
        @DisplayName("同一 target への2つの非ツリー辺は共有レーンで MergePoint ┤ を描画する")
        void shouldRenderMultiSourceMergeWithSharedLane() {
            MigrationNode nodeA = node("a").build();
            MigrationNode nodeB = node("b").build();
            MigrationNode nodeC = node("c").build();
            MigrationNode nodeD = node("d").build();
            LayoutStream childB = new LayoutStream(NodeId.of("a"), List.of(nodeB), List.of());
            LayoutStream childC = new LayoutStream(NodeId.of("a"), List.of(nodeC), List.of());
            LayoutStream rootStream =
                    new LayoutStream(null, List.of(nodeA, nodeD), List.of(childB, childC));

            GridCanvas canvas = new GridCanvas();
            canvas.addStream(rootStream);
            canvas.addNonTreeEdge(NodeId.of("b"), NodeId.of("d"));
            canvas.addNonTreeEdge(NodeId.of("c"), NodeId.of("d"));

            assertThat(canvas.render(n -> n.id().value()))
                    .isEqualTo(
                            """
                            ●   a
                            ├●┐ b
                            ├●┤ c
                            │ │
                            ├─┘
                            ●   d
                            """);
        }

        @Test
        @DisplayName("複数ノードの子ストリームの末尾から非ツリー辺を引くと深い分岐のマージを描画する")
        void shouldRenderDeepChildStreamMerge() {
            MigrationNode nodeA = node("a").build();
            MigrationNode nodeB = node("b").build();
            MigrationNode nodeC = node("c").build();
            MigrationNode nodeD = node("d").build();
            LayoutStream childStream =
                    new LayoutStream(NodeId.of("a"), List.of(nodeB, nodeC), List.of());
            LayoutStream rootStream =
                    new LayoutStream(null, List.of(nodeA, nodeD), List.of(childStream));

            GridCanvas canvas = new GridCanvas();
            canvas.addStream(rootStream);
            canvas.addNonTreeEdge(NodeId.of("c"), NodeId.of("d"));

            assertThat(canvas.render(n -> n.id().value()))
                    .isEqualTo(
                            """
                            ●   a
                            ├●  b
                            ││
                            │●┐ c
                            │ │
                            ├─┘
                            ●   d
                            """);
        }

        @Test
        @DisplayName("重ならない2つの非ツリー辺は同じレーン列を再利用する")
        void shouldReuseLaneColumnForNonOverlappingNonTreeEdges() {
            MigrationNode nodeA = node("a").build();
            MigrationNode nodeB = node("b").build();
            MigrationNode nodeC = node("c").build();
            MigrationNode nodeD = node("d").build();
            MigrationNode nodeE = node("e").build();
            MigrationNode nodeF = node("f").build();
            LayoutStream childE = new LayoutStream(NodeId.of("a"), List.of(nodeE), List.of());
            LayoutStream childF = new LayoutStream(NodeId.of("c"), List.of(nodeF), List.of());
            LayoutStream rootStream =
                    new LayoutStream(
                            null, List.of(nodeA, nodeB, nodeC, nodeD), List.of(childE, childF));

            GridCanvas canvas = new GridCanvas();
            canvas.addStream(rootStream);
            canvas.addNonTreeEdge(NodeId.of("e"), NodeId.of("b"));
            canvas.addNonTreeEdge(NodeId.of("f"), NodeId.of("d"));

            assertThat(canvas.render(n -> n.id().value()))
                    .isEqualTo(
                            """
                            ●   a
                            ├●┐ e
                            │ │
                            ├─┘
                            ●   b
                            │
                            ●   c
                            ├●┐ f
                            │ │
                            ├─┘
                            ●   d
                            """);
        }

        @Test
        @DisplayName("マージ行に上向き接続がない場合は ┌ (DownRight) で描画する")
        void shouldRenderDownRightInMergeRowWhenNoUpwardConnection() {
            MigrationNode nodeA = node("a").build();
            MigrationNode nodeB = node("b").build();
            MigrationNode nodeC = node("c").build();
            MigrationNode nodeD = node("d").build();
            LayoutStream childC = new LayoutStream(NodeId.of("a"), List.of(nodeC), List.of());
            LayoutStream childD = new LayoutStream(NodeId.of("b"), List.of(nodeD), List.of());
            LayoutStream rootStream =
                    new LayoutStream(null, List.of(nodeA, nodeB), List.of(childC, childD));

            GridCanvas canvas = new GridCanvas();
            canvas.addStream(rootStream);
            canvas.addNonTreeEdge(NodeId.of("c"), NodeId.of("d"));

            assertThat(canvas.render(n -> n.id().value()))
                    .isEqualTo(
                            """
                            ●   a
                            ├●┐ c
                            │ │
                            ● │ b
                            │┌┘
                            ├●  d
                            """);
        }

        @Test
        @DisplayName("水平線が MergePoint を横断すると CrossMerge ┼ を描画する")
        void shouldRenderCrossMergeWhenHorizontalCrossesMergePoint() {
            MigrationNode nodeA = node("a").build();
            MigrationNode nodeB = node("b").build();
            MigrationNode nodeC = node("c").build();
            MigrationNode nodeD = node("d").build();
            MigrationNode nodeE = node("e").build();
            LayoutStream childB = new LayoutStream(NodeId.of("a"), List.of(nodeB), List.of());
            LayoutStream childC = new LayoutStream(NodeId.of("a"), List.of(nodeC), List.of());
            LayoutStream rootStream =
                    new LayoutStream(null, List.of(nodeA, nodeD, nodeE), List.of(childB, childC));

            GridCanvas canvas = new GridCanvas();
            canvas.addStream(rootStream);
            canvas.addNonTreeEdge(NodeId.of("b"), NodeId.of("d"));
            canvas.addNonTreeEdge(NodeId.of("c"), NodeId.of("d"));
            canvas.addNonTreeEdge(NodeId.of("c"), NodeId.of("e"));

            assertThat(canvas.render(n -> n.id().value()))
                    .isEqualTo(
                            """
                            ●    a
                            ├●┐  b
                            ├●┼┐ c
                            │ ││
                            ├─┘│
                            ●  │ d
                            │  │
                            ├──┘
                            ●    e
                            """);
        }
    }

    @Nested
    @DisplayName("toNodeLineInfos()")
    class NodeLineInfosTest {

        @Test
        @DisplayName("直線ストリームのノードは column=0 で返す")
        void shouldReturnNodeLineInfosAtColumnZeroForLinearStream() {
            MigrationNode nodeA = node("a").build();
            MigrationNode nodeB = node("b").build();
            LayoutStream stream = new LayoutStream(null, List.of(nodeA, nodeB), List.of());

            GridCanvas canvas = new GridCanvas();
            canvas.addStream(stream);

            List<NodeLineInfo> infos = canvas.toNodeLineInfos();

            assertThat(infos).hasSize(2);
            assertThat(infos.get(0)).isEqualTo(new NodeLineInfo(nodeA, 0));
            assertThat(infos.get(1)).isEqualTo(new NodeLineInfo(nodeB, 0));
        }

        @Test
        @DisplayName("子ストリームのノードは column=1 で返す")
        void shouldReturnNodeLineInfoAtColumnOneForChildStreamNode() {
            MigrationNode nodeA = node("a").build();
            MigrationNode nodeB = node("b").build();
            MigrationNode nodeC = node("c").build();
            LayoutStream childStream = new LayoutStream(NodeId.of("a"), List.of(nodeC), List.of());
            LayoutStream rootStream =
                    new LayoutStream(null, List.of(nodeA, nodeB), List.of(childStream));

            GridCanvas canvas = new GridCanvas();
            canvas.addStream(rootStream);

            List<NodeLineInfo> infos = canvas.toNodeLineInfos();

            assertThat(infos).hasSize(3);
            assertThat(infos.stream().filter(i -> i.node().equals(nodeC)).findFirst())
                    .isPresent()
                    .hasValueSatisfying(info -> assertThat(info.column()).isEqualTo(1));
        }
    }
}
