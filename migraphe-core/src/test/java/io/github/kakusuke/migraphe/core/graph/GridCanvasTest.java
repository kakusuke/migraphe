package io.github.kakusuke.migraphe.core.graph;

import static io.github.kakusuke.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @Test
    @DisplayName("addNonTreeEdge() がダイヤモンド DAG の非ツリー辺を追加すると マージ行が挿入され正しくレンダリングされる")
    void shouldInsertMergeRowAndRenderDiamondNonTreeEdge() {
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

        String result = canvas.render(n -> n.id().value());

        assertThat(result).isEqualTo("●   a\n├●┐ b\n│ │\n● │ c\n│ │\n├─┘\n●   d\n");
    }

    @Test
    @DisplayName("同一 target への2回目の addNonTreeEdge は既存マージ行を再利用する")
    void shouldReuseExistingMergeRowForSecondNonTreeEdgeToSameTarget() {
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").build();
        MigrationNode nodeC = node("c").build();
        MigrationNode nodeD = node("d").build();
        LayoutStream stream =
                new LayoutStream(null, List.of(nodeA, nodeB, nodeC, nodeD), List.of());

        GridCanvas canvas = new GridCanvas();
        canvas.addStream(stream);

        canvas.addNonTreeEdge(NodeId.of("a"), NodeId.of("d"));
        canvas.addNonTreeEdge(NodeId.of("b"), NodeId.of("d"));

        assertThat(canvas.render(n -> n.id().value()))
                .isEqualTo("●┐  a\n││\n●│┐ b\n│││\n●││ c\n│││\n├┴┘\n●   d\n");
    }

    @Test
    @DisplayName("addNonTreeEdge() の水平線が既存レーンの Vertical を横断すると CrossPoint に変換される")
    void shouldConvertVerticalToCrossPointWhenHorizontalCrossesLane() {
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").build();
        MigrationNode nodeC = node("c").build();
        MigrationNode nodeD = node("d").build();
        LayoutStream stream =
                new LayoutStream(null, List.of(nodeA, nodeB, nodeC, nodeD), List.of());

        GridCanvas canvas = new GridCanvas();
        canvas.addStream(stream);
        canvas.addNonTreeEdge(NodeId.of("a"), NodeId.of("d"));
        canvas.addNonTreeEdge(NodeId.of("b"), NodeId.of("d"));

        assertThat(canvas.cellAt(2, 1)).isInstanceOf(Cell.CrossPoint.class);
    }

    @Test
    @DisplayName("addNonTreeEdge() の水平線が ForkToLane を横断すると ForkAndMerge に変換される")
    void shouldConvertForkToLaneToForkAndMergeWhenHorizontalCrossesIt() {
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").build();
        MigrationNode nodeC = node("c").build();
        LayoutStream stream = new LayoutStream(null, List.of(nodeA, nodeB, nodeC), List.of());

        GridCanvas canvas = new GridCanvas();
        canvas.addStream(stream);
        canvas.addNonTreeEdge(NodeId.of("a"), NodeId.of("b"));
        canvas.addNonTreeEdge(NodeId.of("a"), NodeId.of("c"));

        assertThat(canvas.render(n -> n.id().value()))
                .isEqualTo("●┬┐ a\n│││\n├┘│\n● │ b\n│ │\n├─┘\n●   c\n");
    }

    @Test
    @DisplayName("fillNewRow が CrossPoint の下に Vertical を配置する")
    void shouldPlaceVerticalBelowCrossPointWhenFillingNewRow() throws Exception {
        GridCanvas canvas = new GridCanvas();

        Field gridField = GridCanvas.class.getDeclaredField("grid");
        gridField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<List<Cell>> grid = (List<List<Cell>>) gridField.get(canvas);

        List<Cell> row0 = new ArrayList<>();
        row0.add(new Cell.Empty());
        row0.add(new Cell.CrossPoint());
        grid.add(row0);

        MigrationNode nodeA = node("a").build();
        LayoutStream stream = new LayoutStream(null, List.of(nodeA), List.of());
        canvas.addStream(stream);

        // col 0: Node placed by addStream; col 1: Vertical expected below CrossPoint
        assertThat(canvas.cellAt(1, 1)).isInstanceOf(Cell.Vertical.class);
    }

    @Test
    @DisplayName("Step 7b のマージ行構築で既存レーンの縦線がある列には CrossPoint を配置する")
    void shouldPlaceCrossPointInMergeRowWhereVerticalLaneExists() {
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").build();
        MigrationNode nodeC = node("c").build();
        MigrationNode nodeD = node("d").build();
        MigrationNode nodeE = node("e").build();
        LayoutStream stream =
                new LayoutStream(null, List.of(nodeA, nodeB, nodeC, nodeD, nodeE), List.of());

        GridCanvas canvas = new GridCanvas();
        canvas.addStream(stream);
        canvas.addNonTreeEdge(NodeId.of("a"), NodeId.of("e"));
        canvas.addNonTreeEdge(NodeId.of("b"), NodeId.of("d"));

        // Find the merge row for (b→d): it's the StreamFork row just above D
        // Scan for a row where col 0 is StreamFork, col 1 should be CrossPoint, col 2 is
        // LaneToMerge
        boolean found = false;
        for (int r = 0; r < canvas.rowCount(); r++) {
            if (canvas.cellAt(r, 0) instanceof Cell.StreamFork
                    && canvas.cellAt(r, 2) instanceof Cell.LaneToMerge) {
                assertThat(canvas.cellAt(r, 1))
                        .as("merge row %d col 1 should be CrossPoint", r)
                        .isInstanceOf(Cell.CrossPoint.class);
                found = true;
                break;
            }
        }
        assertThat(found).as("merge row for (b→d) should exist").isTrue();
    }

    @Test
    @DisplayName("findOrCreateLaneColumn が空き列を再利用して列の膨張を防ぐ")
    void shouldReuseEmptyColumnInsteadOfAppendingNew() {
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").build();
        MigrationNode nodeC = node("c").build();
        MigrationNode nodeD = node("d").build();
        LayoutStream stream =
                new LayoutStream(null, List.of(nodeA, nodeB, nodeC, nodeD), List.of());

        GridCanvas canvas = new GridCanvas();
        canvas.addStream(stream);
        canvas.addNonTreeEdge(NodeId.of("a"), NodeId.of("b"));
        canvas.addNonTreeEdge(NodeId.of("c"), NodeId.of("d"));

        // Without column reuse: colCount would be 3 (col 0 + col 1 + col 2)
        // With column reuse: col 1 is reused for the second edge, colCount stays 2
        assertThat(canvas.colCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Step 7b の else ブランチで Node の直下に StreamFork がある列には Vertical を配置する")
    void shouldPlaceVerticalInMergeRowWhenNodeImmediatelyAboveStreamForkInElseBranch() {
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").build();
        MigrationNode nodeC = node("c").build();
        MigrationNode nodeD = node("d").build();
        LayoutStream childStream = new LayoutStream(NodeId.of("b"), List.of(nodeD), List.of());
        LayoutStream rootStream =
                new LayoutStream(null, List.of(nodeA, nodeB, nodeC), List.of(childStream));

        GridCanvas canvas = new GridCanvas();
        canvas.addStream(rootStream);
        canvas.addNonTreeEdge(NodeId.of("a"), NodeId.of("d"));

        // Grid before addNonTreeEdge:
        // row 0: Node A (0,0)
        // row 1: Vertical (1,0)
        // row 2: Node B (2,0)
        // row 3: StreamFork (3,0), Node D (3,1)
        // row 4: Vertical (4,0)
        // row 5: Node C (5,0)
        //
        // addNonTreeEdge(A, D): endCol=1, endRow=3, mergeCol=2
        // Merge row inserted at row 3; c=0 is in "else" range (c < endCol=1).
        // cellAt(endRow-1=2, 0) = Node B → hasDownwardConnection=false (BUG: Empty placed).
        // cellAt(endRow=3, 0) = StreamFork → hasUpwardConnection=true (FIX: Vertical).
        assertThat(canvas.render(n -> n.id().value()))
                .isEqualTo("●─┐ a\n│ │\n● │ b\n│├┘\n├●  d\n│\n●   c\n");
    }

    @Test
    @DisplayName("fillLaneVerticals が Horizontal を CrossPoint に変換する")
    void shouldConvertHorizontalToCrossPointInFillLaneVerticals() throws Exception {
        GridCanvas canvas = new GridCanvas();

        Field gridField = GridCanvas.class.getDeclaredField("grid");
        gridField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<List<Cell>> grid = (List<List<Cell>>) gridField.get(canvas);

        Field posField = GridCanvas.class.getDeclaredField("nodePositions");
        posField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<NodeId, int[]> nodePositions = (Map<NodeId, int[]>) posField.get(canvas);

        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").build();

        // row 0: Node A, ForkToLane
        // row 1: Vertical, Horizontal  ← lane col 1 passes through a Horizontal (merge row from a
        // prior edge)
        // row 2: StreamFork, LaneToMerge
        // row 3: Node B
        grid.add(new ArrayList<>(List.of(new Cell.Node(nodeA), new Cell.ForkToLane())));
        grid.add(new ArrayList<>(List.of(new Cell.Vertical(), new Cell.Horizontal())));
        grid.add(new ArrayList<>(List.of(new Cell.StreamFork(), new Cell.LaneToMerge())));
        grid.add(new ArrayList<>(List.of(new Cell.Node(nodeB))));

        nodePositions.put(nodeA.id(), new int[] {0, 0});
        nodePositions.put(nodeB.id(), new int[] {3, 0});

        Method fillMethod =
                GridCanvas.class.getDeclaredMethod(
                        "fillLaneVerticals", NodeId.class, NodeId.class, int.class);
        fillMethod.setAccessible(true);
        fillMethod.invoke(canvas, nodeA.id(), nodeB.id(), 1);

        assertThat(canvas.cellAt(1, 1)).isInstanceOf(Cell.CrossPoint.class);
    }

    @Test
    @DisplayName("Step 7a がマージ行再利用時に Vertical を CrossPoint に変換する")
    void shouldConvertVerticalToCrossPointInStep7aWhenReusingMergeRow() throws Exception {
        GridCanvas canvas = new GridCanvas();

        Field gridField = GridCanvas.class.getDeclaredField("grid");
        gridField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<List<Cell>> grid = (List<List<Cell>>) gridField.get(canvas);

        Field posField = GridCanvas.class.getDeclaredField("nodePositions");
        posField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<NodeId, int[]> nodePositions = (Map<NodeId, int[]>) posField.get(canvas);

        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").build();

        // row 0: Node A, Empty, ForkToLane, Empty
        // row 1: Vertical, Vertical, Vertical, Empty
        // row 2: StreamFork, Vertical, LaneToMerge, Empty  ← existing merge row; col 1 has Vertical
        // row 3: Node B, Empty, Empty, Empty
        grid.add(
                new ArrayList<>(
                        List.of(
                                new Cell.Node(nodeA),
                                new Cell.Empty(),
                                new Cell.ForkToLane(),
                                new Cell.Empty())));
        grid.add(
                new ArrayList<>(
                        List.of(
                                new Cell.Vertical(),
                                new Cell.Vertical(),
                                new Cell.Vertical(),
                                new Cell.Empty())));
        grid.add(
                new ArrayList<>(
                        List.of(
                                new Cell.StreamFork(),
                                new Cell.Vertical(),
                                new Cell.LaneToMerge(),
                                new Cell.Empty())));
        grid.add(
                new ArrayList<>(
                        List.of(
                                new Cell.Node(nodeB),
                                new Cell.Empty(),
                                new Cell.Empty(),
                                new Cell.Empty())));

        nodePositions.put(nodeA.id(), new int[] {0, 0});
        nodePositions.put(nodeB.id(), new int[] {3, 0});

        Method insertMethod =
                GridCanvas.class.getDeclaredMethod(
                        "insertOrReuseMergeRow", NodeId.class, int.class);
        insertMethod.setAccessible(true);
        insertMethod.invoke(canvas, nodeB.id(), 3);

        assertThat(canvas.cellAt(2, 1))
                .as("Step 7a should convert Vertical to CrossPoint when extending merge row")
                .isInstanceOf(Cell.CrossPoint.class);
    }

    @Test
    @DisplayName("render() が CrossPoint を │ (縦線優先)、MergeJunction を ┴ に変換する")
    void shouldRenderCrossPointAndMergeJunctionSymbols() throws Exception {
        GridCanvas canvas = new GridCanvas();

        Field gridField = GridCanvas.class.getDeclaredField("grid");
        gridField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<List<Cell>> grid = (List<List<Cell>>) gridField.get(canvas);

        List<Cell> row0 = new ArrayList<>();
        row0.add(new Cell.CrossPoint());
        List<Cell> row1 = new ArrayList<>();
        row1.add(new Cell.MergeJunction());
        grid.add(row0);
        grid.add(row1);

        String result = canvas.render(n -> n.id().value());

        assertThat(result).isEqualTo("│\n┴\n");
    }
}
