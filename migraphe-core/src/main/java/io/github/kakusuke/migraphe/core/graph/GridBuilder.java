package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.ArrayList;
import java.util.List;

final class GridBuilder {

    static final NodeId VIRTUAL_ROOT = NodeId.of("__virtual_root__");
    static final NodeId VIRTUAL_END = NodeId.of("__virtual_end__");

    record CellPosition(int col, int row) {}

    private final List<List<Cell>> grid;

    GridBuilder(int rows, int cols) {
        this.grid = new ArrayList<>(rows);
        for (int r = 0; r < rows; r++) {
            List<Cell> row = new ArrayList<>(cols);
            for (int c = 0; c < cols; c++) {
                row.add(new Cell.SpaceCell());
            }
            grid.add(row);
        }
    }

    GridBuilder() {
        this.grid = new ArrayList<>(3);
        List<Cell> row0 = new ArrayList<>(1);
        row0.add(new Cell.TaskCell(VIRTUAL_ROOT));
        List<Cell> row1 = new ArrayList<>(1);
        row1.add(new Cell.ConnectorCell(true, true, false, false));
        List<Cell> row2 = new ArrayList<>(1);
        row2.add(new Cell.TaskCell(VIRTUAL_END));
        grid.add(row0);
        grid.add(row1);
        grid.add(row2);
    }

    int rows() {
        return grid.size();
    }

    int cols() {
        return grid.isEmpty() ? 0 : grid.get(0).size();
    }

    Cell get(int row, int col) {
        return grid.get(row).get(col);
    }

    void set(int row, int col, Cell cell) {
        grid.get(row).set(col, cell);
    }

    void addRow() {
        int cols = cols();
        List<Cell> row = new ArrayList<>(cols);
        for (int c = 0; c < cols; c++) {
            row.add(new Cell.SpaceCell());
        }
        grid.add(row);
    }

    void addColumns(int n) {
        for (List<Cell> row : grid) {
            for (int i = 0; i < n; i++) {
                row.add(new Cell.SpaceCell());
            }
        }
    }

    void insertRow(int r) {
        int cols = cols();
        List<Cell> newRow = new ArrayList<>(cols);
        for (int c = 0; c < cols; c++) {
            boolean hasUp =
                    r > 0
                            && grid.get(r - 1).get(c) instanceof Cell.ConnectorCell conn
                            && conn.down();
            boolean hasDown =
                    r < grid.size()
                            && grid.get(r).get(c) instanceof Cell.ConnectorCell conn
                            && conn.up();
            if (hasUp || hasDown) {
                newRow.add(new Cell.ConnectorCell(true, true, false, false));
            } else {
                newRow.add(new Cell.SpaceCell());
            }
        }
        grid.add(r, newRow);
    }

    void insertColumn(int c) {
        for (int r = 0; r < grid.size(); r++) {
            List<Cell> row = grid.get(r);
            boolean hasLeft =
                    c > 0 && row.get(c - 1) instanceof Cell.ConnectorCell conn && conn.right();
            boolean hasRight =
                    c < row.size() && row.get(c) instanceof Cell.ConnectorCell conn && conn.left();
            if (hasLeft || hasRight) {
                row.add(c, new Cell.ConnectorCell(false, false, true, true));
            } else {
                row.add(c, new Cell.SpaceCell());
            }
        }
    }

    List<List<Cell>> toGrid() {
        List<List<Cell>> result = new ArrayList<>(grid.size());
        for (List<Cell> row : grid) {
            result.add(new ArrayList<>(row));
        }
        return result;
    }

    CellPosition getCellPosition(NodeId id) {
        for (int r = 0; r < grid.size(); r++) {
            List<Cell> row = grid.get(r);
            for (int c = 0; c < row.size(); c++) {
                if (row.get(c) instanceof Cell.TaskCell tc && tc.id().equals(id)) {
                    return new CellPosition(c, r);
                }
            }
        }
        throw new IllegalArgumentException("NodeId not found: " + id);
    }

    void addBranch(NodeId forkNodeId, List<NodeId> ids) {
        CellPosition forkPos = getCellPosition(forkNodeId);
        int forkRow = forkPos.row();
        int forkCol = forkPos.col();
        insertColumn(forkCol + 1);
        insertColumn(forkCol + 2);
        int nodeCol = forkCol + 2;
        int connectorCol = forkCol + 1;

        // first node
        insertRow(forkRow + 1);
        set(forkRow + 1, nodeCol, new Cell.TaskCell(ids.get(0)));
        set(forkRow + 1, connectorCol, new Cell.ConnectorCell(false, false, true, true));

        // subsequent nodes
        for (int i = 1; i < ids.size(); i++) {
            int prevRow = getCellPosition(ids.get(i - 1)).row();
            insertRow(prevRow + 1);
            set(prevRow + 1, nodeCol, new Cell.ConnectorCell(true, true, false, false));
            insertRow(prevRow + 2);
            set(prevRow + 2, nodeCol, new Cell.TaskCell(ids.get(i)));
        }
    }

    List<List<Cell>> toVisibleGrid() {
        int lastRow = grid.size() - 1;
        List<List<Cell>> result = new ArrayList<>(lastRow - 1);
        for (int r = 1; r < lastRow; r++) {
            List<Cell> srcRow = grid.get(r);
            List<Cell> visRow = new ArrayList<>(Math.max(0, srcRow.size() - 2));
            for (int c = 2; c < srcRow.size(); c++) {
                visRow.add(srcRow.get(c));
            }
            result.add(visRow);
        }
        return result;
    }

    void drawNonDomEdge(NodeId forkId, NodeId mergeId) {
        int forkRow = getCellPosition(forkId).row();
        int mergeRow = getCellPosition(mergeId).row();
        addColumns(1);
        int laneCol = cols() - 1;

        set(forkRow, laneCol, new Cell.ConnectorCell(false, true, false, false));
        for (int r = forkRow + 1; r < mergeRow; r++) {
            set(r, laneCol, new Cell.ConnectorCell(true, true, false, false));
        }

        insertRow(mergeRow);
        set(mergeRow, laneCol, new Cell.ConnectorCell(true, false, true, false));
    }
}
