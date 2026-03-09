package io.github.kakusuke.migraphe.core.graph;

import static io.github.kakusuke.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GridCanvas")
class GridCanvasTest {

    @Test
    @DisplayName("単一ノードのストリームを追加すると cellAt(0,0) が Cell.Node を返す")
    void shouldPlaceNodeCellAtOriginForSingleNodeStream() {
        MigrationNode nodeA = node("a").build();
        LayoutStream stream = new LayoutStream(null, List.of(nodeA), List.of());

        GridCanvas canvas = new GridCanvas();
        canvas.addStream(stream);

        assertThat(canvas.cellAt(0, 0)).isInstanceOf(Cell.Node.class);
        assertThat(((Cell.Node) canvas.cellAt(0, 0)).node()).isEqualTo(nodeA);
        assertThat(canvas.rowCount()).isEqualTo(1);
        assertThat(canvas.colCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("3ノードの直線ストリームは Node/Vertical/Node/Vertical/Node の5行1列になる")
    void shouldPlaceVerticalCellsBetweenNodesForLinearChain() {
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").build();
        MigrationNode nodeC = node("c").build();
        LayoutStream stream = new LayoutStream(null, List.of(nodeA, nodeB, nodeC), List.of());

        GridCanvas canvas = new GridCanvas();
        canvas.addStream(stream);

        assertThat(canvas.cellAt(0, 0)).isEqualTo(new Cell.Node(nodeA));
        assertThat(canvas.cellAt(1, 0)).isInstanceOf(Cell.Vertical.class);
        assertThat(canvas.cellAt(2, 0)).isEqualTo(new Cell.Node(nodeB));
        assertThat(canvas.cellAt(3, 0)).isInstanceOf(Cell.Vertical.class);
        assertThat(canvas.cellAt(4, 0)).isEqualTo(new Cell.Node(nodeC));
        assertThat(canvas.rowCount()).isEqualTo(5);
        assertThat(canvas.colCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("子ストリームが forkNode の直後に描画される")
    void shouldPlaceStreamForkAndChildNodeForSingleChildStream() {
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").build();
        MigrationNode nodeC = node("c").build();
        LayoutStream childStream = new LayoutStream(NodeId.of("a"), List.of(nodeC), List.of());
        LayoutStream rootStream =
                new LayoutStream(null, List.of(nodeA, nodeB), List.of(childStream));

        GridCanvas canvas = new GridCanvas();
        canvas.addStream(rootStream);

        // ● A        (0,0)
        // ├● C       (1,0) StreamFork, (1,1) Node
        // │          (2,0) Vertical
        // ● B        (3,0)
        assertThat(canvas.cellAt(0, 0)).isEqualTo(new Cell.Node(nodeA));
        assertThat(canvas.cellAt(1, 0)).isInstanceOf(Cell.StreamFork.class);
        assertThat(canvas.cellAt(1, 1)).isEqualTo(new Cell.Node(nodeC));
        assertThat(canvas.cellAt(2, 0)).isInstanceOf(Cell.Vertical.class);
        assertThat(canvas.cellAt(3, 0)).isEqualTo(new Cell.Node(nodeB));
        assertThat(canvas.rowCount()).isEqualTo(4);
        assertThat(canvas.colCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("子ストリームが複数ノードを持つ場合 fillNewRow で親列の縦線が継続する")
    void shouldContinueVerticalLinesForChildStreamWithMultipleNodes() {
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

        // ● A        (0,0) Node
        // ├● C       (1,0) StreamFork, (1,1) Node
        // ││         (2,0) Vertical, (2,1) Vertical
        // │● D       (3,0) Vertical, (3,1) Node
        // │          (4,0) Vertical
        // ● B        (5,0) Node
        assertThat(canvas.cellAt(0, 0)).isEqualTo(new Cell.Node(nodeA));
        assertThat(canvas.cellAt(1, 0)).isInstanceOf(Cell.StreamFork.class);
        assertThat(canvas.cellAt(1, 1)).isEqualTo(new Cell.Node(nodeC));
        assertThat(canvas.cellAt(2, 0)).isInstanceOf(Cell.Vertical.class);
        assertThat(canvas.cellAt(2, 1)).isInstanceOf(Cell.Vertical.class);
        assertThat(canvas.cellAt(3, 0)).isInstanceOf(Cell.Vertical.class);
        assertThat(canvas.cellAt(3, 1)).isEqualTo(new Cell.Node(nodeD));
        assertThat(canvas.cellAt(4, 0)).isInstanceOf(Cell.Vertical.class);
        assertThat(canvas.cellAt(5, 0)).isEqualTo(new Cell.Node(nodeB));
        assertThat(canvas.rowCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("複数の forkNode からそれぞれ子ストリームが分岐する")
    void shouldForkChildStreamsFromDifferentNodes() {
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

        // ● A        (0,0)
        // ├● D       (1,0) StreamFork, (1,1) Node
        // │          (2,0) Vertical
        // ● B        (3,0)
        // ├● E       (4,0) StreamFork, (4,1) Node
        // │          (5,0) Vertical
        // ● C        (6,0)
        assertThat(canvas.cellAt(0, 0)).isEqualTo(new Cell.Node(nodeA));
        assertThat(canvas.cellAt(1, 0)).isInstanceOf(Cell.StreamFork.class);
        assertThat(canvas.cellAt(1, 1)).isEqualTo(new Cell.Node(nodeD));
        assertThat(canvas.cellAt(2, 0)).isInstanceOf(Cell.Vertical.class);
        assertThat(canvas.cellAt(3, 0)).isEqualTo(new Cell.Node(nodeB));
        assertThat(canvas.cellAt(4, 0)).isInstanceOf(Cell.StreamFork.class);
        assertThat(canvas.cellAt(4, 1)).isEqualTo(new Cell.Node(nodeE));
        assertThat(canvas.cellAt(5, 0)).isInstanceOf(Cell.Vertical.class);
        assertThat(canvas.cellAt(6, 0)).isEqualTo(new Cell.Node(nodeC));
        assertThat(canvas.rowCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("toNodeLineInfos() がノード行の NodeLineInfo を column 付きで返す")
    void shouldReturnNodeLineInfosWithCorrectColumns() {
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
    @DisplayName("render() が ASCII グラフとラベルを含む文字列を返す")
    void shouldRenderAsciiGraphWithLabels() {
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").build();
        LayoutStream stream = new LayoutStream(null, List.of(nodeA, nodeB), List.of());

        GridCanvas canvas = new GridCanvas();
        canvas.addStream(stream);

        String result = canvas.render(n -> n.id().value());

        assertThat(result).isEqualTo("● a\n│\n● b\n");
    }
}
