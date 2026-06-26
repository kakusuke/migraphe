package io.github.kakusuke.migraphe.core.graph.layout;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A 2D grid of {@link Cell}s onto which a {@link LayoutStream} tree is drawn, and from which ASCII
 * text is rendered.
 *
 * <p>This is the third stage of the layout pipeline ({@code MigrationGraph -> LayoutSort ->
 * LayoutTree -> GridCanvas -> ExecutionGraphView}). The root stream is placed with {@link
 * #addStream(LayoutStream)}; each non-tree edge is then routed with {@link #addNonTreeEdge(NodeId,
 * NodeId)}; redundant filler rows are dropped with {@link #removeRedundantRows()}; and finally
 * {@link #render(Function)} converts the cells to box- drawing characters. Rows and columns
 * auto-expand and auto-connect as cells are placed.
 */
public final class GridCanvas {

    /** Creates a new {@code GridCanvas}. */
    public GridCanvas() {}

    private final Grid internalGrid = new Grid();

    /**
     * Draws a stream (and recursively its child streams) onto the grid, starting on a freshly
     * appended row in column 0.
     *
     * @param stream the root stream to place; child streams fork to the right as they are visited
     */
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

    /**
     * Sets the cell at the given position, growing the row with {@link Cell.Empty} padding as
     * needed.
     *
     * @param row the zero-based row index
     * @param col the zero-based column index
     * @param cell the cell to place
     */
    public void setCell(int row, int col, Cell cell) {
        internalGrid.setCell(row, col, cell);
    }

    /**
     * Returns the cell at the given position.
     *
     * @param row the zero-based row index
     * @param col the zero-based column index
     * @return the cell at the position, or a {@link Cell.Empty} if it is out of bounds
     */
    public Cell cellAt(int row, int col) {
        return internalGrid.cellAt(row, col);
    }

    /**
     * Returns the number of rows currently in the grid.
     *
     * @return the row count
     */
    public int rowCount() {
        return internalGrid.rowCount();
    }

    /**
     * Returns the number of columns in the grid (the width of its widest row).
     *
     * @return the column count
     */
    public int colCount() {
        return internalGrid.colCount();
    }

    /**
     * Collects the placement info for every node cell on the grid, scanned row by row then column
     * by column.
     *
     * @return one {@link NodeLineInfo} per {@link Cell.Node} found, in scan order
     */
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

    /**
     * Routes a non-tree edge from {@code source} to {@code target} onto the grid (bottom-up,
     * anchored at the target).
     *
     * <p>Does nothing if either endpoint is not present, or if the source is on the same row as or
     * below the target (an upward edge, which {@link LayoutTree} guarantees never occurs).
     * Otherwise it inserts or reuses a merge row above the target, selects a vertical routing lane
     * column, and fills the horizontal and vertical connectors that join source to target.
     *
     * @param source the upstream (dependency) node id
     * @param target the downstream (dependent) node id
     */
    public void addNonTreeEdge(NodeId source, NodeId target) {
        int[] srcPos = internalGrid.nodePosition(source);
        int[] tgtPos = internalGrid.nodePosition(target);
        if (srcPos == null || tgtPos == null) {
            return;
        }
        // Skip drawing if source is below target (or on the same row)
        if (srcPos[0] >= tgtPos[0]) {
            return;
        }
        int mergeRowIdx = insertOrReuseMergeRow(target);
        int laneCol = selectLaneColumn(source, target, mergeRowIdx);
        drawMergeRowHorizontal(mergeRowIdx, target, laneCol);
        drawSourceHorizontal(source, laneCol);
        fillLaneVerticals(source, mergeRowIdx, laneCol);
    }

    @SuppressWarnings("NullAway") // caller guarantees target exists in nodePositions
    private int insertOrReuseMergeRow(NodeId target) {
        int[] tgtPos = internalGrid.nodePosition(target);
        int endRow = tgtPos[0];
        int endCol = tgtPos[1];

        // Reuse existing merge row if present
        Cell endColCellAbove = cellAt(endRow - 1, endCol);
        if (endColCellAbove instanceof Cell.StreamFork
                || endColCellAbove instanceof Cell.DownRight) {
            return endRow - 1;
        }

        // Insert new merge row
        internalGrid.insertRow(endRow);

        Cell above = cellAt(endRow - 1, endCol);
        if (above.connectsDown()) {
            setCell(endRow, endCol, new Cell.StreamFork());
        } else {
            setCell(endRow, endCol, new Cell.DownRight());
        }
        return endRow;
    }

    @SuppressWarnings("NullAway") // caller guarantees source/target exist in nodePositions
    private int selectLaneColumn(NodeId source, NodeId target, int mergeRowIdx) {
        int[] srcPos = internalGrid.nodePosition(source);
        int[] tgtPos = internalGrid.nodePosition(target);
        int startRow = srcPos[0];
        int startCol = srcPos[1];
        int endCol = tgtPos[1];

        int minCol = Math.max(startCol, endCol) + 1;
        int colCount = colCount();

        // Priority 1: Reuse existing lane (lane sharing)
        Cell mergeRowTargetCell = cellAt(mergeRowIdx, endCol);
        if (mergeRowTargetCell instanceof Cell.StreamFork
                || mergeRowTargetCell instanceof Cell.DownRight) {
            for (int c = minCol; c < colCount(); c++) {
                Cell mergeCell = cellAt(mergeRowIdx, c);
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

        // Priority 2: Reuse lane with gap (verticals don't reach source)
        for (int c = minCol; c < colCount; c++) {
            Cell mergeCell = cellAt(mergeRowIdx, c);
            if (!(mergeCell instanceof Cell.LaneToMerge
                    || mergeCell instanceof Cell.MergeJunction)) {
                continue;
            }
            // Scan upward from merge row to find top of existing lane
            int topOfLane = -1;
            for (int r = mergeRowIdx - 1; r > startRow; r--) {
                Cell cell = cellAt(r, c);
                if (cell instanceof Cell.Vertical
                        || cell instanceof Cell.CrossPoint
                        || cell instanceof Cell.MergePoint
                        || cell instanceof Cell.CrossMerge) {
                    continue;
                } else if (cell instanceof Cell.ForkToLane || cell instanceof Cell.ForkAndMerge) {
                    topOfLane = r;
                    break;
                } else {
                    break;
                }
            }
            if (topOfLane == -1) continue;
            // Check gap: all cells from startRow to topOfLane must be Empty/Horizontal
            boolean gapClear = true;
            for (int r = startRow; r < topOfLane; r++) {
                Cell cell = cellAt(r, c);
                if (!(cell instanceof Cell.Empty || cell instanceof Cell.Horizontal)) {
                    gapClear = false;
                    break;
                }
            }
            if (gapClear) {
                Cell topCell = cellAt(topOfLane, c);
                if (topCell instanceof Cell.ForkToLane) {
                    setCell(topOfLane, c, new Cell.MergePoint());
                } else if (topCell instanceof Cell.ForkAndMerge) {
                    setCell(topOfLane, c, new Cell.CrossMerge());
                }
                return c;
            }
        }

        // Priority 3: Find empty column from source to merge row
        for (int c = minCol; c < colCount; c++) {
            boolean canReuse = true;
            for (int r = startRow; r <= mergeRowIdx; r++) {
                if (!(cellAt(r, c) instanceof Cell.Empty)) {
                    canReuse = false;
                    break;
                }
            }
            if (canReuse) {
                return c;
            }
        }

        // Priority 4: Insert new column
        return internalGrid.insertColumn(colCount);
    }

    @SuppressWarnings("NullAway") // caller guarantees target exists in nodePositions
    private void drawMergeRowHorizontal(int mergeRowIdx, NodeId target, int laneCol) {
        // Skip if lane already has LaneToMerge/MergeJunction (Priority 1 reuse)
        Cell laneCell = cellAt(mergeRowIdx, laneCol);
        if (laneCell instanceof Cell.LaneToMerge || laneCell instanceof Cell.MergeJunction) {
            return;
        }

        int[] tgtPos = internalGrid.nodePosition(target);
        int endCol = tgtPos[1];

        for (int c = endCol + 1; c < laneCol; c++) {
            Cell existing = cellAt(mergeRowIdx, c);
            if (existing instanceof Cell.LaneToMerge) {
                setCell(mergeRowIdx, c, new Cell.MergeJunction());
            } else if (existing instanceof Cell.Empty) {
                setCell(mergeRowIdx, c, new Cell.Horizontal());
            } else if (existing instanceof Cell.Vertical) {
                setCell(mergeRowIdx, c, new Cell.CrossPoint());
            }
        }
        setCell(mergeRowIdx, laneCol, new Cell.LaneToMerge());
    }

    @SuppressWarnings("NullAway") // caller guarantees source exists in nodePositions
    private void drawSourceHorizontal(NodeId source, int laneCol) {
        int[] srcPos = internalGrid.nodePosition(source);
        int startRow = srcPos[0];
        int startCol = srcPos[1];

        // Place ForkToLane at laneCol (skip if MergePoint — already placed by selectLaneColumn)
        Cell laneCell = cellAt(startRow, laneCol);
        if (!(laneCell instanceof Cell.MergePoint)) {
            setCell(startRow, laneCol, new Cell.ForkToLane());
        }

        for (int c = startCol + 1; c < laneCol; c++) {
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

    @SuppressWarnings("NullAway") // caller guarantees source exists in nodePositions
    private void fillLaneVerticals(NodeId source, int mergeRowIdx, int laneCol) {
        int[] srcPos = internalGrid.nodePosition(source);
        int startRow = srcPos[0];

        for (int r = startRow + 1; r < mergeRowIdx; r++) {
            Cell existing = cellAt(r, laneCol);
            if (existing instanceof Cell.Empty) {
                setCell(r, laneCol, new Cell.Vertical());
            } else if (existing instanceof Cell.Horizontal) {
                setCell(r, laneCol, new Cell.CrossPoint());
            }
        }
    }

    /**
     * Removes redundant rows: rows containing only {@link Cell.Vertical}/{@link Cell.Empty} cells
     * that do not bridge a node directly above to a node directly below.
     */
    public void removeRedundantRows() {
        internalGrid.removeRedundantRows();
    }

    /**
     * Renders the grid as ASCII text using box-drawing characters.
     *
     * <p>Each cell maps to a glyph; rows containing a node append a space and that node's label.
     * Lines without a node are right-trimmed, and every line is newline-terminated.
     *
     * @param labelFn function returning the label to append for the node found on a row
     * @return the multi-line rendered grid
     */
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

    /**
     * The mutable backing store of the canvas: a ragged list of rows plus a node-id-to-position
     * index.
     *
     * <p>Inserting a row or column auto-fills connecting {@link Cell.Vertical}/{@link
     * Cell.Horizontal} cells based on the neighbors' {@code connects*} predicates, and keeps the
     * {@code nodePositions} index in sync as indices shift.
     */
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

        void removeRedundantRows() {
            for (int r = rows.size() - 1; r >= 0; r--) {
                if (isRedundantRow(r)) {
                    rows.remove(r);
                    for (int[] pos : nodePositions.values()) {
                        if (pos[0] > r) {
                            pos[0]--;
                        }
                    }
                }
            }
        }

        private boolean isRedundantRow(int r) {
            List<Cell> row = rows.get(r);
            boolean hasNodeBridge = false;
            for (int c = 0; c < row.size(); c++) {
                Cell cell = row.get(c);
                if (!(cell instanceof Cell.Vertical) && !(cell instanceof Cell.Empty)) {
                    return false;
                }
                if (cellAt(r - 1, c) instanceof Cell.Node
                        && cellAt(r + 1, c) instanceof Cell.Node) {
                    hasNodeBridge = true;
                }
            }
            return !hasNodeBridge;
        }
    }
}
