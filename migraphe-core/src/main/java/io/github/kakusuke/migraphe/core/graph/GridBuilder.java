package io.github.kakusuke.migraphe.core.graph;

import java.util.ArrayList;
import java.util.List;

final class GridBuilder {

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
}
