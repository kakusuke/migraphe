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
                || cell instanceof Cell.MergePoint
                || cell instanceof Cell.ForkToLane
                || cell instanceof Cell.ForkAndMerge;
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
                    case Cell.Fork ignored -> line.append("└");
                    case Cell.Horizontal ignored -> line.append("─");
                    case Cell.ForkToLane ignored -> line.append("┐");
                    case Cell.MergePoint ignored -> line.append("┤");
                    case Cell.LaneToMerge ignored -> line.append("┘");
                    case Cell.ForkAndMerge ignored -> line.append("┬");
                    case Cell.Empty ignored -> line.append(" ");
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
