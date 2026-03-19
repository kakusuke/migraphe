package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** ストリームツリーをグリッドセルに配置するキャンバス。 */
public final class GridCanvas {

    private final Grid internalGrid = new Grid();

    /** ストリームをグリッドに追加する。 */
    public void addStream(LayoutStream stream) {
        int firstRow = internalGrid.insertRow(internalGrid.rowCount());
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
                internalGrid.setCell(currentRow, col, new Cell.Node(node));
            } else {
                currentRow = internalGrid.insertRow(internalGrid.rowCount());
                internalGrid.setCell(currentRow, col, new Cell.Vertical());

                currentRow = internalGrid.insertRow(internalGrid.rowCount());
                internalGrid.setCell(currentRow, col, new Cell.Node(node));
            }

            List<LayoutStream> children = childMap.get(node.id());
            if (children != null) {
                for (LayoutStream child : children) {
                    int forkRow = internalGrid.insertRow(internalGrid.rowCount());
                    internalGrid.setCell(forkRow, col, new Cell.StreamFork());
                    drawStream(child, col + 1, forkRow);
                }
            }
        }
    }

    /** 指定位置のセルを設定する。 */
    public void setCell(int row, int col, Cell cell) {
        internalGrid.setCell(row, col, cell);
    }

    /** 指定位置のセルを返す。範囲外は Cell.Empty を返す。 */
    public Cell cellAt(int row, int col) {
        return internalGrid.cellAt(row, col);
    }

    /** グリッドの行数を返す。 */
    public int rowCount() {
        return internalGrid.rowCount();
    }

    /** グリッドの列数（最大列数）を返す。 */
    public int colCount() {
        return internalGrid.colCount();
    }

    /** ノード位置情報リストを返す。 */
    public List<NodeLineInfo> toNodeLineInfos() {
        List<NodeLineInfo> result = new ArrayList<>();
        for (int r = 0; r < internalGrid.rowCount(); r++) {
            for (int c = 0; c < internalGrid.colCount(); c++) {
                Cell cell = internalGrid.cellAt(r, c);
                if (cell instanceof Cell.Node nodeCell) {
                    result.add(new NodeLineInfo(nodeCell.node(), c));
                }
            }
        }
        return result;
    }

    /** 非ツリー辺をグリッドに追加する。 */
    public void addNonTreeEdge(NodeId source, NodeId target) {
        int[] srcPos = internalGrid.nodePosition(source);
        int[] tgtPos = internalGrid.nodePosition(target);
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
        int[] srcPos = internalGrid.nodePosition(source);
        int[] tgtPos = internalGrid.nodePosition(target);
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
            for (int c = minCol; c < colCount(); c++) {
                Cell mergeCell = cellAt(endRow - 1, c);
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
        int mergeCol = internalGrid.insertColumn(colCount);
        setCell(startRow, mergeCol, new Cell.ForkToLane());
        return mergeCol;
    }

    @SuppressWarnings("NullAway") // caller guarantees source exists in nodePositions
    private void drawHorizontalToLane(NodeId source, int mergeCol) {
        int[] srcPos = internalGrid.nodePosition(source);
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
        int[] tgtPos = internalGrid.nodePosition(target);
        int endRow = tgtPos[0];
        int endCol = tgtPos[1];

        // Step 7a: Check if existing merge row can be reused
        Cell endColCellAbove = cellAt(endRow - 1, endCol);
        if (endColCellAbove instanceof Cell.StreamFork
                || endColCellAbove instanceof Cell.DownRight) {
            int mergeRowIdx = endRow - 1;
            for (int c = endCol + 1; c < mergeCol; c++) {
                Cell existing = cellAt(mergeRowIdx, c);
                if (existing instanceof Cell.LaneToMerge) {
                    setCell(mergeRowIdx, c, new Cell.MergeJunction());
                } else if (existing instanceof Cell.Empty) {
                    setCell(mergeRowIdx, c, new Cell.Horizontal());
                } else if (existing instanceof Cell.Vertical) {
                    setCell(mergeRowIdx, c, new Cell.CrossPoint());
                }
            }
            setCell(mergeRowIdx, mergeCol, new Cell.LaneToMerge());
            return;
        }

        // Step 7b: Insert merge row using Grid.insertRow (auto-fills verticals)
        internalGrid.insertRow(endRow);

        // Overlay merge-specific cells
        Cell above = cellAt(endRow - 1, endCol);
        if (above.connectsDown()) {
            setCell(endRow, endCol, new Cell.StreamFork());
        } else {
            setCell(endRow, endCol, new Cell.DownRight());
        }
        for (int c = endCol + 1; c < mergeCol; c++) {
            Cell existing = cellAt(endRow, c);
            if (existing instanceof Cell.Vertical) {
                setCell(endRow, c, new Cell.CrossPoint());
            } else {
                setCell(endRow, c, new Cell.Horizontal());
            }
        }
        setCell(endRow, mergeCol, new Cell.LaneToMerge());
    }

    @SuppressWarnings("NullAway") // caller guarantees source/target exist in nodePositions
    private void fillLaneVerticals(NodeId source, NodeId target, int mergeCol) {
        int[] srcPos = internalGrid.nodePosition(source);
        int[] tgtPos = internalGrid.nodePosition(target);
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
        for (int r = 0; r < internalGrid.rowCount(); r++) {
            List<Cell> row = internalGrid.rows().get(r);
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

    static final class Grid {
        private final List<List<Cell>> rows = new ArrayList<>();
        private final Map<NodeId, int[]> nodePositions = new HashMap<>();

        List<List<Cell>> rows() {
            return rows;
        }

        int insertRow(int index) {
            int cols = colCount();
            List<Cell> newRow = new ArrayList<>();
            int lastNonEmpty = -1;
            for (int c = 0; c < cols; c++) {
                Cell above = (index > 0) ? cellAt(index - 1, c) : new Cell.Empty();
                Cell below = (index < rows.size()) ? cellAt(index, c) : new Cell.Empty();
                if (above.connectsDown() || below.connectsUp()) {
                    newRow.add(new Cell.Vertical());
                    lastNonEmpty = c;
                } else {
                    newRow.add(new Cell.Empty());
                }
            }
            // Trim trailing Empty cells
            if (lastNonEmpty + 1 < newRow.size()) {
                newRow.subList(lastNonEmpty + 1, newRow.size()).clear();
            }
            rows.add(index, newRow);
            for (int[] pos : nodePositions.values()) {
                if (pos[0] >= index) {
                    pos[0]++;
                }
            }
            return index;
        }

        int insertColumn(int index) {
            for (int r = 0; r < rows.size(); r++) {
                Cell left = (index > 0) ? cellAt(r, index - 1) : new Cell.Empty();
                Cell right = cellAt(r, index);
                Cell newCell =
                        (left.connectsRight() || right.connectsLeft())
                                ? new Cell.Horizontal()
                                : new Cell.Empty();
                List<Cell> rowList = rows.get(r);
                while (rowList.size() < index) {
                    rowList.add(new Cell.Empty());
                }
                rowList.add(index, newCell);
            }
            for (int[] pos : nodePositions.values()) {
                if (pos[1] >= index) {
                    pos[1]++;
                }
            }
            return index;
        }

        void setCell(int row, int col, Cell cell) {
            List<Cell> rowList = rows.get(row);
            while (rowList.size() <= col) {
                rowList.add(new Cell.Empty());
            }
            rowList.set(col, cell);
            if (cell instanceof Cell.Node n) {
                nodePositions.put(n.node().id(), new int[] {row, col});
            }
        }

        Cell cellAt(int row, int col) {
            if (row < 0 || row >= rows.size()) {
                return new Cell.Empty();
            }
            List<Cell> rowList = rows.get(row);
            if (col < 0 || col >= rowList.size()) {
                return new Cell.Empty();
            }
            return rowList.get(col);
        }

        int rowCount() {
            return rows.size();
        }

        int colCount() {
            int max = 0;
            for (List<Cell> row : rows) {
                if (row.size() > max) {
                    max = row.size();
                }
            }
            return max;
        }

        int @Nullable [] nodePosition(NodeId id) {
            return nodePositions.get(id);
        }
    }
}
