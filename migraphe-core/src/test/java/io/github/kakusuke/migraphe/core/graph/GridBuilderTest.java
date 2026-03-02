package io.github.kakusuke.migraphe.core.graph;

import static org.assertj.core.api.Assertions.assertThat;

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
}
