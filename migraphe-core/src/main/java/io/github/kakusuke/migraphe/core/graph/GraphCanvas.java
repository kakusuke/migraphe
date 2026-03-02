package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.*;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * DAG の実行グラフを ASCII レンダリングするキャンバス。
 *
 * <p>使い方:
 *
 * <pre>{@code
 * GraphCanvas canvas = new GraphCanvas();
 * canvas.layout(dt);
 * List<String> lines = canvas.render(n -> n.id().value());
 * }</pre>
 */
final class GraphCanvas {

    private static final NodeId VIRTUAL_ROOT = NodeId.of("__virtual_root__");

    private sealed interface Row permits Row.NodeRow, Row.ConnectorRow, Row.MergeRow, Row.BlankRow {
        record NodeRow(
                MigrationNode node,
                int column,
                boolean isBranch,
                boolean isLastChild,
                Set<Integer> activeColumns)
                implements Row {}

        record ConnectorRow(int column, Set<Integer> activeColumns) implements Row {}

        record MergeRow(int column, Set<Integer> activeColumns) implements Row {}

        record BlankRow() implements Row {}
    }

    private @Nullable DominatorTree dt;
    private List<Row> initialRows;
    private List<NodeLineInfo> lineInfos;
    private List<NonDomEdge> nonDomEdges;
    private int maxColumn;

    GraphCanvas() {
        this.dt = null;
        this.initialRows = new ArrayList<>();
        this.lineInfos = new ArrayList<>();
        this.nonDomEdges = new ArrayList<>();
        this.maxColumn = 0;
    }

    // ========== Phase 1: レイアウト ==========

    void layout(DominatorTree dt) {
        this.dt = dt;
        this.initialRows = new ArrayList<>();
        this.lineInfos = new ArrayList<>();
        this.nonDomEdges = new ArrayList<>();

        if (dt.nodeMap.isEmpty()) return;

        // 非支配木辺の特定
        this.nonDomEdges.addAll(dt.findNonDomEdges());

        Map<NodeId, List<NodeId>> domChildren = dt.domChildren;
        Map<NodeId, @Nullable NodeId> trunkChild = dt.trunkChild;
        List<NodeId> roots = dt.roots;
        boolean hasVirtualRoot = dt.hasVirtualRoot;

        // DFS レンダリング
        if (hasVirtualRoot) {
            List<NodeId> virtualChildren = domChildren.getOrDefault(VIRTUAL_ROOT, List.of());
            @Nullable NodeId virtualTrunk = trunkChild.get(VIRTUAL_ROOT);

            List<NodeId> virtualBranches = new ArrayList<>();
            for (NodeId child : virtualChildren) {
                if (!child.equals(virtualTrunk)) virtualBranches.add(child);
            }

            BranchClassification classification =
                    classifyBranches(virtualTrunk, virtualBranches, domChildren, nonDomEdges);

            boolean first = true;
            // Pre-trunk branches
            for (NodeId child : classification.preTrunk()) {
                if (!first) {
                    addSubgraphSeparator();
                }
                emitSubtree(child, 0, false, false, Set.of(), domChildren, trunkChild);
                first = false;
            }
            // Trunk
            if (virtualTrunk != null) {
                if (!first) {
                    addSubgraphSeparator();
                }
                emitSubtree(virtualTrunk, 0, false, false, Set.of(), domChildren, trunkChild);
                first = false;
            }
            // Post-trunk branches
            for (NodeId child : classification.postTrunk()) {
                if (!first) {
                    addSubgraphSeparator();
                }
                emitSubtree(child, 0, false, false, Set.of(), domChildren, trunkChild);
                first = false;
            }
        } else {
            emitSubtree(roots.get(0), 0, false, false, Set.of(), domChildren, trunkChild);
        }

        int mc = 0;
        for (NodeLineInfo info : lineInfos) {
            mc = Math.max(mc, info.column());
        }
        this.maxColumn = mc;
    }

    // ========== Phase 2: 文字列化 ==========

    List<String> render(Function<MigrationNode, String> labelFn) {
        return renderGrid(labelFn);
    }

    // ========== 結果 ==========

    List<NodeLineInfo> getNodeLineInfos() {
        return List.copyOf(lineInfos);
    }

    List<String> renderGrid(Function<MigrationNode, String> labelFn) {
        Map<NodeId, String> labels = new LinkedHashMap<>();
        for (NodeLineInfo info : lineInfos) {
            labels.put(info.node().id(), labelFn.apply(info.node()));
        }

        List<List<Cell>> grid = buildGrid();
        List<String> output = new ArrayList<>();
        int treeWidth = maxColumn > 0 ? 2 * maxColumn + 1 : 1;
        boolean hasLanes = grid.stream().anyMatch(row -> row.size() > treeWidth);
        for (List<Cell> rowCells : grid) {
            StringBuilder sb = new StringBuilder();
            NodeId nodeId = null;
            for (Cell cell : rowCells) {
                sb.append(cell.symbol());
                if (cell instanceof Cell.TaskCell tc) nodeId = tc.id();
            }
            String line;
            if (nodeId != null && labels.containsKey(nodeId)) {
                String graphStr = hasLanes ? sb.toString() : sb.toString().stripTrailing();
                line = (graphStr + " " + labels.get(nodeId)).stripTrailing();
            } else {
                line = sb.toString().stripTrailing();
            }
            output.add(line);
        }
        return output;
    }

    List<List<Cell>> buildGrid() {
        int gridWidth = maxColumn > 0 ? 2 * maxColumn + 1 : 1;
        List<List<Cell>> grid = new ArrayList<>();
        for (Row row : initialRows) {
            List<Cell> rowCells =
                    new ArrayList<>(Collections.nCopies(gridWidth, new Cell.SpaceCell()));
            if (row instanceof Row.NodeRow nr) {
                if (nr.isBranch()) {
                    int forkGridCol = 2 * (nr.column() - 1);
                    rowCells.set(forkGridCol, new Cell.ConnectorCell(true, true, false, true));
                    if (forkGridCol + 1 < gridWidth)
                        rowCells.set(
                                forkGridCol + 1, new Cell.ConnectorCell(false, false, true, true));
                    rowCells.set(2 * nr.column(), new Cell.TaskCell(nr.node().id()));
                    for (int c : nr.activeColumns()) {
                        int gridCol = 2 * c;
                        if (gridCol < forkGridCol && gridCol < gridWidth)
                            rowCells.set(gridCol, new Cell.ConnectorCell(true, true, false, false));
                    }
                } else {
                    rowCells.set(2 * nr.column(), new Cell.TaskCell(nr.node().id()));
                    applyActiveColumnBars(rowCells, nr.activeColumns(), gridWidth, nr.column());
                }
            } else if (row instanceof Row.ConnectorRow cr) {
                rowCells.set(2 * cr.column(), new Cell.ConnectorCell(true, true, false, false));
                applyActiveColumnBars(rowCells, cr.activeColumns(), gridWidth, -1);
            }
            grid.add(rowCells);
        }

        if (!nonDomEdges.isEmpty()) {
            Map<NodeId, Integer> nodeRowIndex = new LinkedHashMap<>();
            for (int i = 0; i < initialRows.size(); i++) {
                if (initialRows.get(i) instanceof Row.NodeRow nr) {
                    nodeRowIndex.put(nr.node().id(), i);
                }
            }

            Map<NodeId, List<NonDomEdge>> edgesByTarget = new LinkedHashMap<>();
            for (NonDomEdge edge : nonDomEdges) {
                edgesByTarget.computeIfAbsent(edge.target(), k -> new ArrayList<>()).add(edge);
            }

            List<GroupInfo> groups = new ArrayList<>();
            for (Map.Entry<NodeId, List<NonDomEdge>> entry : edgesByTarget.entrySet()) {
                NodeId target = entry.getKey();
                List<NonDomEdge> edges = entry.getValue();
                List<NodeId> sources =
                        edges.stream()
                                .map(NonDomEdge::source)
                                .sorted(
                                        Comparator.comparingInt(
                                                s -> nodeRowIndex.getOrDefault(s, 0)))
                                .toList();
                int startRow =
                        sources.stream()
                                .mapToInt(s -> nodeRowIndex.getOrDefault(s, 0))
                                .min()
                                .orElse(0);
                Integer endRowObj = nodeRowIndex.get(target);
                if (endRowObj == null) continue;
                groups.add(new GroupInfo(target, sources, startRow, endRowObj));
            }
            groups.sort(
                    Comparator.<GroupInfo>comparingInt(GroupInfo::endRow)
                            .reversed()
                            .thenComparingInt(GroupInfo::startRow));

            int[] laneMinStartRow = new int[groups.size()];
            Arrays.fill(laneMinStartRow, Integer.MAX_VALUE);
            int lc = 0;
            int[] groupLane = new int[groups.size()];
            for (int g = 0; g < groups.size(); g++) {
                GroupInfo group = groups.get(g);
                int lane = -1;
                for (int i = 0; i < lc; i++) {
                    if (group.endRow() > laneMinStartRow[i]) continue;
                    boolean higherLaneOverlaps = false;
                    for (int m = i + 1; m < lc; m++) {
                        if (laneMinStartRow[m] < group.endRow()) {
                            higherLaneOverlaps = true;
                            break;
                        }
                    }
                    if (!higherLaneOverlaps) {
                        lane = i;
                        break;
                    }
                }
                if (lane == -1) lane = lc++;
                groupLane[g] = lane;
                laneMinStartRow[lane] = Math.min(laneMinStartRow[lane], group.startRow());
            }

            boolean[][] laneActive = new boolean[initialRows.size()][lc];
            Map<NodeId, List<int[]>> nodeLaneActions = new LinkedHashMap<>();
            Map<NodeId, Integer> targetLaneMap = new LinkedHashMap<>();
            for (int g = 0; g < groups.size(); g++) {
                GroupInfo group = groups.get(g);
                int lane = groupLane[g];
                for (int r = group.startRow(); r < group.endRow(); r++) {
                    laneActive[r][lane] = true;
                }
                targetLaneMap.put(group.target(), lane);
                boolean first = true;
                for (NodeId source : group.sources()) {
                    int action = first ? 0 : 1;
                    nodeLaneActions
                            .computeIfAbsent(source, k -> new ArrayList<>())
                            .add(new int[] {lane, action});
                    first = false;
                }
            }

            for (int i = 0; i < grid.size(); i++) {
                List<Cell> rowCells = grid.get(i);
                rowCells.add(new Cell.SpaceCell());
                int sepIdx = rowCells.size() - 1;
                Row row = initialRows.get(i);
                if (row instanceof Row.NodeRow nr) {
                    NodeId nodeId = nr.node().id();
                    List<int[]> actions = nodeLaneActions.getOrDefault(nodeId, List.of());
                    for (int l = 0; l < lc; l++) {
                        rowCells.add(computeNodeLaneCellForGrid(i, l, actions, laneActive));
                    }
                } else {
                    for (int l = 0; l < lc; l++) {
                        rowCells.add(
                                laneActive[i][l]
                                        ? new Cell.ConnectorCell(true, true, false, false)
                                        : new Cell.SpaceCell());
                    }
                }
                List<Cell> laneArea = rowCells.subList(sepIdx + 1, rowCells.size());
                boolean shouldDrawSepAsHBar = laneArea.stream().anyMatch(this::isVisibleLaneCell);
                if (shouldDrawSepAsHBar)
                    rowCells.set(sepIdx, new Cell.ConnectorCell(false, false, true, true));
                if (shouldDrawSepAsHBar && row instanceof Row.NodeRow nr2 && !nr2.isBranch()) {
                    int nodeGridCol = 2 * nr2.column();
                    for (int gc = nodeGridCol + 1; gc < sepIdx; gc++) {
                        if (rowCells.get(gc) instanceof Cell.SpaceCell) {
                            rowCells.set(gc, new Cell.ConnectorCell(false, false, true, true));
                        }
                    }
                }
            }

            List<Map.Entry<NodeId, Integer>> targets = new ArrayList<>(targetLaneMap.entrySet());
            targets.sort(Comparator.comparingInt(e -> nodeRowIndex.get(e.getKey())));

            int rowOffset = 0;
            for (Map.Entry<NodeId, Integer> entry : targets) {
                NodeId targetId = entry.getKey();
                int targetLane = entry.getValue();

                Integer targetRowObj = nodeRowIndex.get(targetId);
                if (targetRowObj == null) continue;
                int targetRow = targetRowObj + rowOffset;
                if (targetRow <= 0) continue;

                boolean isBranchTarget =
                        initialRows.get(targetRowObj) instanceof Row.NodeRow tnr && tnr.isBranch();
                int treeWidth = maxColumn > 0 ? 2 * maxColumn + 1 : 1;
                int col = 0;
                for (int gc = 0; gc < treeWidth; gc += 2) {
                    if (grid.get(targetRow).get(gc) instanceof Cell.TaskCell tc
                            && tc.id().equals(targetId)) {
                        col = gc / 2;
                        break;
                    }
                }
                int mergeJoinGridCol = isBranchTarget ? 2 * (col - 1) : 2 * col;

                int gridTotalWidth = grid.get(targetRow).size();
                List<Cell> mergeRow =
                        new ArrayList<>(Collections.nCopies(gridTotalWidth, new Cell.SpaceCell()));
                grid.add(targetRow, mergeRow);

                List<Cell> aboveRow = grid.get(targetRow - 1);

                for (int gc = 0; gc < mergeJoinGridCol; gc++) {
                    mergeRow.set(
                            gc,
                            aboveRow.get(gc) instanceof Cell.ConnectorCell
                                    ? new Cell.ConnectorCell(true, true, false, false)
                                    : new Cell.SpaceCell());
                }

                mergeRow.set(mergeJoinGridCol, new Cell.ConnectorCell(true, true, false, true));
                for (int gc = mergeJoinGridCol + 1; gc < treeWidth; gc++) {
                    mergeRow.set(gc, new Cell.ConnectorCell(false, false, true, true));
                }

                if (treeWidth < gridTotalWidth) {
                    mergeRow.set(treeWidth, new Cell.ConnectorCell(false, false, true, true));
                }

                for (int l = 0; l < lc; l++) {
                    int laneGridCol = treeWidth + 1 + l;
                    Cell prevLaneCell = aboveRow.get(laneGridCol);
                    boolean isConnecting =
                            prevLaneCell instanceof Cell.ConnectorCell cc && cc.down();

                    Cell laneCell;
                    if (l == targetLane) {
                        laneCell = new Cell.ConnectorCell(true, false, true, false);
                    } else if (l < targetLane) {
                        laneCell =
                                isConnecting
                                        ? new Cell.ConnectorCell(true, true, true, true)
                                        : new Cell.ConnectorCell(false, false, true, true);
                    } else {
                        laneCell =
                                isConnecting
                                        ? new Cell.ConnectorCell(true, true, false, false)
                                        : new Cell.SpaceCell();
                    }
                    mergeRow.set(laneGridCol, laneCell);
                }

                rowOffset++;
            }
        }

        return grid;
    }

    private boolean isVisibleLaneCell(Cell c) {
        return c instanceof Cell.ConnectorCell cc && cc.left();
    }

    private Cell computeNodeLaneCellForGrid(
            int rowIndex, int lane, List<int[]> actions, boolean[][] laneActive) {
        for (int[] action : actions) {
            if (action[0] == lane) {
                return action[1] == 0
                        ? new Cell.ConnectorCell(false, true, true, false)
                        : new Cell.ConnectorCell(true, true, true, false);
            }
        }
        return laneActive[rowIndex][lane]
                ? new Cell.ConnectorCell(true, true, false, false)
                : new Cell.SpaceCell();
    }

    private void applyActiveColumnBars(
            List<Cell> rowCells, Set<Integer> activeColumns, int gridWidth, int excludeColumn) {
        for (int c : activeColumns) {
            if (c != excludeColumn && 2 * c < gridWidth)
                rowCells.set(2 * c, new Cell.ConnectorCell(true, true, false, false));
        }
    }

    // ========== 支配木 DFS レンダリング ==========

    private void addSubgraphSeparator() {
        if (!lineInfos.isEmpty()) {
            NodeLineInfo last = lineInfos.get(lineInfos.size() - 1);
            @Nullable NodeId prevRoot = dt().subgraphRoot(last.node().id());
            if (prevRoot != null && dt().hasVisibleStructure(prevRoot)) {
                initialRows.add(new Row.BlankRow());
            }
        }
    }

    private void emitSubtree(
            NodeId nodeId,
            int column,
            boolean isBranch,
            boolean isLastChild,
            Set<Integer> activeColumns,
            Map<NodeId, List<NodeId>> domChildren,
            Map<NodeId, @Nullable NodeId> trunkChild) {
        MigrationNode node = dt().nodeMap.get(nodeId);
        if (node == null) return;

        lineInfos.add(new NodeLineInfo(node, column));
        initialRows.add(
                new Row.NodeRow(node, column, isBranch, isLastChild, new TreeSet<>(activeColumns)));

        List<NodeId> children = domChildren.getOrDefault(nodeId, List.of());
        @Nullable NodeId trunk = trunkChild.get(nodeId);
        List<NodeId> branches = new ArrayList<>();
        for (NodeId child : children) {
            if (!child.equals(trunk)) branches.add(child);
        }

        // branch を pre-trunk と post-trunk に分類
        BranchClassification classification =
                classifyBranches(trunk, branches, domChildren, nonDomEdges);
        List<NodeId> preTrunk = classification.preTrunk();
        List<NodeId> postTrunk = classification.postTrunk();

        boolean hasPostTrunk = !postTrunk.isEmpty();

        // Pre-trunk branches
        if (!preTrunk.isEmpty()) {
            Set<Integer> branchActive = new TreeSet<>(activeColumns);
            branchActive.add(column);

            for (NodeId branch : preTrunk) {
                emitSubtree(branch, column + 1, true, false, branchActive, domChildren, trunkChild);
            }
        }

        // Trunk
        if (trunk != null) {
            if (preTrunk.isEmpty() && !hasPostTrunk) {
                // 純粋なチェーン → コネクタ行を挿入
                initialRows.add(new Row.ConnectorRow(column, new TreeSet<>(activeColumns)));
            } else if (preTrunk.isEmpty()) {
                // post-trunk のみ → コネクタ行を挿入
                initialRows.add(new Row.ConnectorRow(column, new TreeSet<>(activeColumns)));
            }

            // post-trunk がある場合、trunk サブツリー中も column を active に保つ
            Set<Integer> trunkActive;
            if (hasPostTrunk) {
                trunkActive = new TreeSet<>(activeColumns);
                trunkActive.add(column);
            } else {
                trunkActive = activeColumns;
            }

            emitSubtree(trunk, column, false, false, trunkActive, domChildren, trunkChild);
        }

        // Post-trunk branches
        if (hasPostTrunk) {
            for (int i = 0; i < postTrunk.size(); i++) {
                boolean isLast = (i == postTrunk.size() - 1);
                Set<Integer> ptActive;
                if (isLast) {
                    ptActive = new TreeSet<>(activeColumns);
                } else {
                    ptActive = new TreeSet<>(activeColumns);
                    ptActive.add(column);
                }
                emitSubtree(
                        postTrunk.get(i),
                        column + 1,
                        true,
                        isLast,
                        ptActive,
                        domChildren,
                        trunkChild);
            }
        }
    }

    // ========== 非支配木辺 ==========

    private BranchClassification classifyBranches(
            @Nullable NodeId trunk,
            List<NodeId> branches,
            Map<NodeId, List<NodeId>> domChildren,
            List<NonDomEdge> nonDomEdges) {
        if (trunk == null || branches.isEmpty() || nonDomEdges.isEmpty()) {
            return new BranchClassification(branches, List.of());
        }

        Set<NodeId> trunkSubtree = collectSubtreeNodes(trunk, domChildren);
        List<NodeId> preTrunk = new ArrayList<>();
        List<NodeId> postTrunk = new ArrayList<>();

        for (NodeId branch : branches) {
            Set<NodeId> branchSubtree = collectSubtreeNodes(branch, domChildren);
            boolean hasNonDomFromTrunk =
                    nonDomEdges.stream()
                            .anyMatch(
                                    e ->
                                            trunkSubtree.contains(e.source())
                                                    && branchSubtree.contains(e.target()));
            if (hasNonDomFromTrunk) {
                postTrunk.add(branch);
            } else {
                preTrunk.add(branch);
            }
        }

        return new BranchClassification(preTrunk, postTrunk);
    }

    private Set<NodeId> collectSubtreeNodes(NodeId root, Map<NodeId, List<NodeId>> domChildren) {
        Set<NodeId> result = new LinkedHashSet<>();
        Deque<NodeId> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            NodeId node = stack.pop();
            result.add(node);
            for (NodeId child : domChildren.getOrDefault(node, List.of())) {
                stack.push(child);
            }
        }
        return result;
    }

    // ========== ユーティリティ ==========

    private DominatorTree dt() {
        if (dt == null) throw new IllegalStateException("layout() が呼ばれていません");
        return dt;
    }
}
