package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** ストリームツリーをグリッドセルに配置するキャンバス。 */
public final class GridCanvas {

    private final List<List<Cell>> grid = new ArrayList<>();
    private final Map<NodeId, int[]> nodePositions = new HashMap<>();

    /** ストリームをグリッドに追加する。 */
    public void addStream(LayoutStream stream) {
        int firstRow = appendRow();
        fillNewRow(firstRow);
        drawStream(stream, 0, firstRow);
    }

    private void drawStream(LayoutStream stream, int col, int firstNodeRow) {
        Map<NodeId, List<LayoutStream>> childMap = new HashMap<>();
        for (LayoutStream child : stream.childStreams()) {
            childMap.computeIfAbsent(child.forkNode(), k -> new ArrayList<>()).add(child);
        }

        int currentRow = firstNodeRow;
        List<MigrationNode> nodes = stream.nodes();

        for (int i = 0; i < nodes.size(); i++) {
            MigrationNode node = nodes.get(i);

            if (i == 0) {
                setCell(currentRow, col, new Cell.Node(node));
            } else {
                currentRow = appendRow();
                fillNewRow(currentRow);
                setCell(currentRow, col, new Cell.Vertical());

                currentRow = appendRow();
                fillNewRow(currentRow);
                setCell(currentRow, col, new Cell.Node(node));
            }

            nodePositions.put(node.id(), new int[] {currentRow, col});

            List<LayoutStream> children = childMap.get(node.id());
            if (children != null) {
                for (LayoutStream child : children) {
                    int forkRow = appendRow();
                    fillNewRow(forkRow);
                    setCell(forkRow, col, new Cell.StreamFork());
                    drawStream(child, col + 1, forkRow);
                }
            }
        }
    }

    private int appendRow() {
        grid.add(new ArrayList<>());
        return grid.size() - 1;
    }

    private void fillNewRow(int row) {
        if (row == 0) {
            return;
        }
        int maxCol = colCount();
        for (int c = 0; c < maxCol; c++) {
            Cell above = cellAt(row - 1, c);
            if (hasDownwardConnection(above)) {
                setCell(row, c, new Cell.Vertical());
            }
        }
    }

    private boolean hasDownwardConnection(Cell cell) {
        return cell instanceof Cell.Vertical
                || cell instanceof Cell.StreamFork
                || cell instanceof Cell.DownRight
                || cell instanceof Cell.MergePoint
                || cell instanceof Cell.ForkToLane
                || cell instanceof Cell.ForkAndMerge
                || cell instanceof Cell.CrossPoint
                || cell instanceof Cell.CrossMerge;
    }

    private boolean hasUpwardConnection(Cell cell) {
        return cell instanceof Cell.Vertical
                || cell instanceof Cell.StreamFork
                || cell instanceof Cell.CrossPoint
                || cell instanceof Cell.CrossMerge
                || cell instanceof Cell.MergePoint
                || cell instanceof Cell.LaneToMerge
                || cell instanceof Cell.MergeJunction;
    }

    private boolean hasVerticalAt(int row, int col) {
        Cell above = cellAt(row - 1, col);
        Cell below = cellAt(row, col);
        return hasDownwardConnection(above) || hasUpwardConnection(below);
    }

    private void setCell(int row, int col, Cell cell) {
        while (grid.size() <= row) {
            grid.add(new ArrayList<>());
        }
        List<Cell> rowList = grid.get(row);
        while (rowList.size() <= col) {
            rowList.add(new Cell.Empty());
        }
        rowList.set(col, cell);
    }

    /** 指定位置のセルを返す。範囲外は Cell.Empty を返す。 */
    public Cell cellAt(int row, int col) {
        if (row < 0 || row >= grid.size()) {
            return new Cell.Empty();
        }
        List<Cell> rowList = grid.get(row);
        if (col < 0 || col >= rowList.size()) {
            return new Cell.Empty();
        }
        return rowList.get(col);
    }

    /** グリッドの行数を返す。 */
    public int rowCount() {
        return grid.size();
    }

    /** グリッドの列数（最大列数）を返す。 */
    public int colCount() {
        int max = 0;
        for (List<Cell> row : grid) {
            if (row.size() > max) {
                max = row.size();
            }
        }
        return max;
    }

    /** ノード位置情報リストを返す。 */
    public List<NodeLineInfo> toNodeLineInfos() {
        List<NodeLineInfo> result = new ArrayList<>();
        for (List<Cell> row : grid) {
            for (int col = 0; col < row.size(); col++) {
                if (row.get(col) instanceof Cell.Node nodeCell) {
                    result.add(new NodeLineInfo(nodeCell.node(), col));
                }
            }
        }
        return result;
    }

    /** 非ツリー辺をグリッドに追加する。 */
    public void addNonTreeEdge(NodeId source, NodeId target) {
        int[] srcPos = nodePositions.get(source);
        int[] tgtPos = nodePositions.get(target);
        if (srcPos == null || tgtPos == null) {
            return;
        }
        int mergeCol = findOrCreateLaneColumn(source, target);
        drawHorizontalToLane(source, mergeCol);
        insertOrReuseMergeRow(target, mergeCol);
        fillLaneVerticals(source, target, mergeCol);
    }

    @SuppressWarnings("NullAway") // caller guarantees source/target exist in nodePositions
    private int findOrCreateLaneColumn(NodeId source, NodeId target) {
        int[] srcPos = nodePositions.get(source);
        int[] tgtPos = nodePositions.get(target);
        int startRow = srcPos[0];
        int startCol = srcPos[1];
        int endCol = tgtPos[1];
        int endRow = tgtPos[0];

        // Try to find an existing empty column to reuse (must be right of both source and target)
        int minCol = Math.max(startCol, endCol) + 1;
        int colCount = colCount();

        // Check for existing lane to same target (lane sharing)
        Cell mergeRowCheck = cellAt(endRow - 1, endCol);
        if (mergeRowCheck instanceof Cell.StreamFork || mergeRowCheck instanceof Cell.DownRight) {
            List<Cell> existingMergeRow = grid.get(endRow - 1);
            for (int c = minCol; c < existingMergeRow.size(); c++) {
                Cell mergeCell = existingMergeRow.get(c);
                if (mergeCell instanceof Cell.LaneToMerge
                        || mergeCell instanceof Cell.MergeJunction) {
                    Cell atSource = cellAt(startRow, c);
                    if (atSource instanceof Cell.Vertical || atSource instanceof Cell.CrossPoint) {
                        setCell(startRow, c, new Cell.MergePoint());
                        return c;
                    }
                }
            }
        }

        for (int c = minCol; c < colCount; c++) {
            boolean canReuse = true;
            for (int r = startRow; r <= endRow; r++) {
                if (!(cellAt(r, c) instanceof Cell.Empty)) {
                    canReuse = false;
                    break;
                }
            }
            if (canReuse) {
                setCell(startRow, c, new Cell.ForkToLane());
                return c;
            }
        }

        // No empty column found, append new one
        int mergeCol = colCount;
        for (List<Cell> row : grid) {
            while (row.size() < mergeCol) {
                row.add(new Cell.Empty());
            }
            row.add(new Cell.Empty());
        }
        setCell(startRow, mergeCol, new Cell.ForkToLane());
        return mergeCol;
    }

    @SuppressWarnings("NullAway") // caller guarantees source exists in nodePositions
    private void drawHorizontalToLane(NodeId source, int mergeCol) {
        int[] srcPos = nodePositions.get(source);
        int startRow = srcPos[0];
        int startCol = srcPos[1];

        for (int c = startCol + 1; c < mergeCol; c++) {
            Cell current = cellAt(startRow, c);
            if (current instanceof Cell.Empty) {
                setCell(startRow, c, new Cell.Horizontal());
            } else if (current instanceof Cell.ForkToLane) {
                setCell(startRow, c, new Cell.ForkAndMerge());
            } else if (current instanceof Cell.MergePoint) {
                setCell(startRow, c, new Cell.CrossMerge());
            } else if (current instanceof Cell.Vertical) {
                setCell(startRow, c, new Cell.CrossPoint());
            }
        }
    }

    @SuppressWarnings("NullAway") // caller guarantees target exists in nodePositions
    private void insertOrReuseMergeRow(NodeId target, int mergeCol) {
        int[] tgtPos = nodePositions.get(target);
        int endRow = tgtPos[0];
        int endCol = tgtPos[1];

        // Step 7a: Check if existing merge row can be reused
        Cell endColCellAbove = cellAt(endRow - 1, endCol);
        if (endColCellAbove instanceof Cell.StreamFork
                || endColCellAbove instanceof Cell.DownRight) {
            int mergeRowIdx = endRow - 1;
            List<Cell> existingMergeRow = grid.get(mergeRowIdx);
            for (int c = endCol + 1; c < mergeCol; c++) {
                Cell existing = existingMergeRow.get(c);
                if (existing instanceof Cell.LaneToMerge) {
                    existingMergeRow.set(c, new Cell.MergeJunction());
                } else if (existing instanceof Cell.Empty) {
                    existingMergeRow.set(c, new Cell.Horizontal());
                } else if (existing instanceof Cell.Vertical) {
                    existingMergeRow.set(c, new Cell.CrossPoint());
                }
            }
            existingMergeRow.set(mergeCol, new Cell.LaneToMerge());
            return;
        }

        // Step 7b: Build merge row and insert at endRow
        int colCount = colCount();
        List<Cell> mergeRow = new ArrayList<>();
        for (int c = 0; c < colCount; c++) {
            if (c == endCol) {
                Cell above = cellAt(endRow - 1, c);
                if (hasDownwardConnection(above)) {
                    mergeRow.add(new Cell.StreamFork());
                } else {
                    mergeRow.add(new Cell.DownRight());
                }
            } else if (c > endCol && c < mergeCol) {
                if (hasVerticalAt(endRow, c)) {
                    mergeRow.add(new Cell.CrossPoint());
                } else {
                    mergeRow.add(new Cell.Horizontal());
                }
            } else if (c == mergeCol) {
                mergeRow.add(new Cell.LaneToMerge());
            } else {
                if (hasVerticalAt(endRow, c)) {
                    mergeRow.add(new Cell.Vertical());
                } else {
                    mergeRow.add(new Cell.Empty());
                }
            }
        }
        grid.add(endRow, mergeRow);

        // Update nodePositions for all nodes at or below endRow (they shifted down by 1)
        for (int[] pos : nodePositions.values()) {
            if (pos[0] >= endRow) {
                pos[0]++;
            }
        }
    }

    @SuppressWarnings("NullAway") // caller guarantees source/target exist in nodePositions
    private void fillLaneVerticals(NodeId source, NodeId target, int mergeCol) {
        int[] srcPos = nodePositions.get(source);
        int[] tgtPos = nodePositions.get(target);
        int startRow = srcPos[0];
        int endRow = tgtPos[0];

        for (int r = startRow + 1; r < endRow; r++) {
            Cell existing = cellAt(r, mergeCol);
            if (existing instanceof Cell.Empty) {
                setCell(r, mergeCol, new Cell.Vertical());
            } else if (existing instanceof Cell.Horizontal) {
                setCell(r, mergeCol, new Cell.CrossPoint());
            }
        }
    }

    /** グリッドをテキスト表現で返す。 */
    public String render(Function<MigrationNode, String> labelFn) {
        StringBuilder sb = new StringBuilder();
        for (List<Cell> row : grid) {
            StringBuilder line = new StringBuilder();
            MigrationNode nodeInRow = null;
            for (Cell cell : row) {
                switch (cell) {
                    case Cell.Node n -> {
                        line.append("●");
                        nodeInRow = n.node();
                    }
                    case Cell.Vertical ignored -> line.append("│");
                    case Cell.StreamFork ignored -> line.append("├");
                    case Cell.DownRight ignored -> line.append("┌");
                    case Cell.Fork ignored -> line.append("└");
                    case Cell.Horizontal ignored -> line.append("─");
                    case Cell.ForkToLane ignored -> line.append("┐");
                    case Cell.MergePoint ignored -> line.append("┤");
                    case Cell.LaneToMerge ignored -> line.append("┘");
                    case Cell.ForkAndMerge ignored -> line.append("┬");
                    case Cell.Empty ignored -> line.append(" ");
                    case Cell.CrossPoint ignored -> line.append("│");
                    case Cell.MergeJunction ignored -> line.append("┴");
                    case Cell.CrossMerge ignored -> line.append("┼");
                }
            }
            if (nodeInRow != null) {
                line.append(" ").append(labelFn.apply(nodeInRow));
            } else {
                int end = line.length();
                while (end > 0 && line.charAt(end - 1) == ' ') end--;
                line.setLength(end);
            }
            sb.append(line).append("\n");
        }
        return sb.toString();
    }
}
