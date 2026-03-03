package io.github.kakusuke.migraphe.core.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GridBuilder")
class GridBuilderTest {

    @Test
    @DisplayName("コンストラクタはすべてのセルを SpaceCell で初期化する")
    void constructorInitializesGridWithSpaceCells() {
        GridBuilder grid = new GridBuilder(2, 3);

        assertThat(grid.rows()).isEqualTo(2);
        assertThat(grid.cols()).isEqualTo(3);
        assertThat(grid.get(0, 0)).isInstanceOf(Cell.SpaceCell.class);
        assertThat(grid.get(1, 2)).isInstanceOf(Cell.SpaceCell.class);
    }

    @Test
    @DisplayName("set と get で Cell を設定・取得できる")
    void setAndGetCell() {
        GridBuilder grid = new GridBuilder(2, 2);
        Cell.TaskCell taskCell = new Cell.TaskCell(NodeId.of("a"));

        grid.set(0, 0, taskCell);

        assertThat(grid.get(0, 0)).isEqualTo(taskCell);
    }

    @Test
    @DisplayName("toGrid は正しい 2D リスト構造を返す")
    void toGridReturnsCorrectStructure() {
        GridBuilder grid = new GridBuilder(2, 2);
        Cell.TaskCell cellA = new Cell.TaskCell(NodeId.of("a"));
        Cell.ConnectorCell cellB = new Cell.ConnectorCell(true, true, false, false);

        grid.set(0, 0, cellA);
        grid.set(1, 1, cellB);

        List<List<Cell>> result = grid.toGrid();
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).hasSize(2);
        assertThat(result.get(0).get(0)).isEqualTo(cellA);
        assertThat(result.get(1).get(1)).isEqualTo(cellB);
        assertThat(result.get(0).get(1)).isInstanceOf(Cell.SpaceCell.class);
        assertThat(result.get(1).get(0)).isInstanceOf(Cell.SpaceCell.class);
    }

    @Test
    @DisplayName("insertRow は既存行を下にずらす")
    void insertRowShiftsExistingRows() {
        GridBuilder grid = new GridBuilder(3, 2);
        Cell.TaskCell taskCell = new Cell.TaskCell(NodeId.of("x"));

        grid.set(1, 0, taskCell);
        grid.insertRow(1);

        assertThat(grid.rows()).isEqualTo(4);
        assertThat(grid.get(1, 0)).isInstanceOf(Cell.SpaceCell.class);
        assertThat(grid.get(1, 1)).isInstanceOf(Cell.SpaceCell.class);
        assertThat(grid.get(2, 0)).isEqualTo(taskCell);
    }

    @Test
    @DisplayName("insertRow は上下の ConnectorCell を縦パススルーする")
    void insertRowPassesThroughVerticalConnectors() {
        GridBuilder grid = new GridBuilder(2, 2);
        Cell.ConnectorCell upper = new Cell.ConnectorCell(false, true, false, false);
        Cell.ConnectorCell lower = new Cell.ConnectorCell(true, false, false, false);

        grid.set(0, 0, upper);
        grid.set(1, 0, lower);
        grid.insertRow(1);

        Cell inserted = grid.get(1, 0);
        assertThat(inserted).isInstanceOf(Cell.ConnectorCell.class);
        Cell.ConnectorCell connector = (Cell.ConnectorCell) inserted;
        assertThat(connector.up()).isTrue();
        assertThat(connector.down()).isTrue();
        assertThat(connector.left()).isFalse();
        assertThat(connector.right()).isFalse();

        assertThat(grid.get(1, 1)).isInstanceOf(Cell.SpaceCell.class);
    }

    @Test
    @DisplayName("insertColumn は既存列を右にずらす")
    void insertColumnShiftsExistingColumns() {
        GridBuilder grid = new GridBuilder(2, 3);
        Cell.TaskCell taskCell = new Cell.TaskCell(NodeId.of("x"));

        grid.set(0, 1, taskCell);
        grid.insertColumn(1);

        assertThat(grid.cols()).isEqualTo(4);
        assertThat(grid.get(0, 1)).isInstanceOf(Cell.SpaceCell.class);
        assertThat(grid.get(1, 1)).isInstanceOf(Cell.SpaceCell.class);
        assertThat(grid.get(0, 2)).isEqualTo(taskCell);
    }

    @Test
    @DisplayName("insertColumn は左右の ConnectorCell を横パススルーする")
    void insertColumnPassesThroughHorizontalConnectors() {
        GridBuilder grid = new GridBuilder(2, 2);
        Cell.ConnectorCell left = new Cell.ConnectorCell(false, false, false, true);
        Cell.ConnectorCell right = new Cell.ConnectorCell(false, false, true, false);

        grid.set(0, 0, left);
        grid.set(0, 1, right);
        grid.insertColumn(1);

        Cell inserted = grid.get(0, 1);
        assertThat(inserted).isInstanceOf(Cell.ConnectorCell.class);
        Cell.ConnectorCell connector = (Cell.ConnectorCell) inserted;
        assertThat(connector.up()).isFalse();
        assertThat(connector.down()).isFalse();
        assertThat(connector.left()).isTrue();
        assertThat(connector.right()).isTrue();

        assertThat(grid.get(1, 1)).isInstanceOf(Cell.SpaceCell.class);
    }

    @Test
    @DisplayName("addRow は末尾に SpaceCell 行を追加する")
    void addRowAppendsSpaceCellRow() {
        GridBuilder grid = new GridBuilder(2, 3);

        grid.addRow();

        assertThat(grid.rows()).isEqualTo(3);
        assertThat(grid.get(2, 0)).isInstanceOf(Cell.SpaceCell.class);
        assertThat(grid.get(2, 1)).isInstanceOf(Cell.SpaceCell.class);
        assertThat(grid.get(2, 2)).isInstanceOf(Cell.SpaceCell.class);
    }

    @Test
    @DisplayName("addColumns は既存全行の末尾に SpaceCell 列を追加する")
    void addColumnsAppendsSpaceCellColumns() {
        GridBuilder grid = new GridBuilder(2, 2);
        Cell.TaskCell taskCell = new Cell.TaskCell(NodeId.of("a"));
        grid.set(0, 0, taskCell);

        grid.addColumns(3);

        assertThat(grid.cols()).isEqualTo(5);
        assertThat(grid.get(0, 0)).isEqualTo(taskCell);
        assertThat(grid.get(0, 2)).isInstanceOf(Cell.SpaceCell.class);
        assertThat(grid.get(0, 4)).isInstanceOf(Cell.SpaceCell.class);
        assertThat(grid.get(1, 4)).isInstanceOf(Cell.SpaceCell.class);
    }

    // ========== 新 API (virtual trunk) テスト ==========

    @Test
    @DisplayName("引数なしコンストラクタは 3 行 × 1 列の仮想 trunk グリッドを生成する")
    void noArgConstructorCreatesVirtualTrunk() {
        GridBuilder grid = new GridBuilder();

        assertThat(grid.rows()).isEqualTo(3);
        assertThat(grid.cols()).isEqualTo(1);
        // row 0: VIRTUAL_ROOT
        assertThat(grid.get(0, 0)).isInstanceOf(Cell.TaskCell.class);
        // row 1: connector │
        assertThat(grid.get(1, 0)).isInstanceOf(Cell.ConnectorCell.class);
        Cell.ConnectorCell connector = (Cell.ConnectorCell) grid.get(1, 0);
        assertThat(connector.up()).isTrue();
        assertThat(connector.down()).isTrue();
        // row 2: VIRTUAL_END
        assertThat(grid.get(2, 0)).isInstanceOf(Cell.TaskCell.class);
    }

    @Test
    @DisplayName("getCellPosition は指定 NodeId の (col, row) を返す")
    void getCellPositionReturnsCorrectCoordinates() {
        GridBuilder grid = new GridBuilder(2, 3);
        NodeId id = NodeId.of("node-x");
        grid.set(1, 2, new Cell.TaskCell(id));

        GridBuilder.CellPosition pos = grid.getCellPosition(id);

        assertThat(pos.col()).isEqualTo(2);
        assertThat(pos.row()).isEqualTo(1);
    }

    @Test
    @DisplayName("getCellPosition は存在しない NodeId で例外を投げる")
    void getCellPositionThrowsForUnknownId() {
        GridBuilder grid = new GridBuilder(2, 2);

        assertThatThrownBy(() -> grid.getCellPosition(NodeId.of("missing")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("addBranch は引数なしコンストラクタで VIRTUAL_ROOT に trunk を追加できる")
    void addBranchAddsTrunkFromVirtualRoot() {
        GridBuilder grid = new GridBuilder();
        NodeId a = NodeId.of("a");
        NodeId b = NodeId.of("b");

        grid.addBranch(GridBuilder.VIRTUAL_ROOT, List.of(a, b));

        // trunk nodes should be present in the grid
        assertThat(grid.rows()).isGreaterThan(3); // more rows than initial 3
        // getCellPosition should work for a and b
        GridBuilder.CellPosition posA = grid.getCellPosition(a);
        GridBuilder.CellPosition posB = grid.getCellPosition(b);
        // b should be below a
        assertThat(posB.row()).isGreaterThan(posA.row());
        // both in same column (trunk column)
        assertThat(posA.col()).isEqualTo(posB.col());
    }

    @Test
    @DisplayName("toVisibleGrid は virtual trunk 行・列を除外して返す")
    void toVisibleGridExcludesVirtualTrunk() {
        GridBuilder grid = new GridBuilder();
        NodeId a = NodeId.of("a");

        grid.addBranch(GridBuilder.VIRTUAL_ROOT, List.of(a));

        List<List<Cell>> visible = grid.toVisibleGrid();
        // VIRTUAL_ROOT row (row 0) and VIRTUAL_END row (last row) are excluded
        // cols 0 and 1 (virtual trunk columns) are excluded
        assertThat(visible).isNotEmpty();
        // first visible row should contain TaskCell(a)
        boolean foundA =
                visible.stream()
                        .anyMatch(
                                row ->
                                        row.stream()
                                                .anyMatch(
                                                        c ->
                                                                c instanceof Cell.TaskCell tc
                                                                        && tc.id().equals(a)));
        assertThat(foundA).isTrue();
    }

    @Test
    @DisplayName("addBranch で実ノードから分岐でき、fork より後に挿入される")
    void addBranchFromActualNodeInsertsAfterFork() {
        GridBuilder grid = new GridBuilder();
        NodeId a = NodeId.of("a");
        NodeId b = NodeId.of("b");
        NodeId branch = NodeId.of("branch");

        grid.addBranch(GridBuilder.VIRTUAL_ROOT, List.of(a, b));
        grid.addBranch(a, List.of(branch));

        GridBuilder.CellPosition posA = grid.getCellPosition(a);
        GridBuilder.CellPosition posB = grid.getCellPosition(b);
        GridBuilder.CellPosition posBranch = grid.getCellPosition(branch);

        // branch should be between a and b row-wise
        assertThat(posBranch.row()).isGreaterThan(posA.row());
        assertThat(posB.row()).isGreaterThan(posBranch.row());
        // branch should be in a different column from trunk
        assertThat(posBranch.col()).isNotEqualTo(posA.col());
    }

    // ========== Cycle 2: drawNonDomEdge テスト ==========

    @Test
    @DisplayName("drawNonDomEdge は forkId 行から mergeId 行の直前までレーンを追加する")
    void drawNonDomEdgeAddsLaneBetweenNodes() {
        GridBuilder grid = new GridBuilder();
        NodeId a = NodeId.of("a");
        NodeId b = NodeId.of("b");
        NodeId c = NodeId.of("c");

        // trunk: a -> b -> c
        grid.addBranch(GridBuilder.VIRTUAL_ROOT, List.of(a, b, c));

        int colsBefore = grid.cols();
        grid.drawNonDomEdge(a, c);

        // a new lane column should be added
        assertThat(grid.cols()).isGreaterThan(colsBefore);
    }

    @Test
    @DisplayName("drawNonDomEdge は mergeId 行の直前にマージ行（┘ を含む）を挿入する")
    void drawNonDomEdgeInsertsMergeRowBeforeMergeNode() {
        GridBuilder grid = new GridBuilder();
        NodeId a = NodeId.of("a");
        NodeId b = NodeId.of("b");
        NodeId c = NodeId.of("c");

        grid.addBranch(GridBuilder.VIRTUAL_ROOT, List.of(a, b, c));
        grid.drawNonDomEdge(a, c);

        List<List<Cell>> visible = grid.toVisibleGrid();
        // There should be a row with ┘ (up+left connector) somewhere
        boolean hasMergeClose =
                visible.stream()
                        .anyMatch(
                                row ->
                                        row.stream()
                                                .anyMatch(
                                                        cell ->
                                                                cell
                                                                                instanceof
                                                                                Cell.ConnectorCell
                                                                                                cc
                                                                        && cc.up()
                                                                        && cc.left()
                                                                        && !cc.down()
                                                                        && !cc.right()));
        assertThat(hasMergeClose).isTrue();
    }
}
