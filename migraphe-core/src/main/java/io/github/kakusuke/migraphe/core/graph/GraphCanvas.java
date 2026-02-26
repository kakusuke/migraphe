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
        Map<NodeId, String> labels = new LinkedHashMap<>();
        for (NodeLineInfo info : lineInfos) {
            labels.put(info.node().id(), labelFn.apply(info.node()));
        }

        List<Row> finalRows;
        int laneCount;
        List<String> rowLaneChars;

        if (nonDomEdges.isEmpty()) {
            finalRows = List.copyOf(initialRows);
            laneCount = 0;
            rowLaneChars = Collections.nCopies(initialRows.size(), "");
        } else {
            LaneResult laneResult = assignLanesAndInsertMergeRows(initialRows, nonDomEdges);
            finalRows = List.copyOf(laneResult.rows);
            laneCount = laneResult.laneCount;
            rowLaneChars = List.copyOf(laneResult.laneChars);
        }

        List<String> allLines = doRender(finalRows, laneCount, rowLaneChars, labels);

        return allLines;
    }

    // ========== 結果 ==========

    List<NodeLineInfo> getNodeLineInfos() {
        return List.copyOf(lineInfos);
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

    // ========== レーン割り当て ==========

    private record LaneResult(List<Row> rows, int laneCount, List<String> laneChars) {}

    private LaneResult assignLanesAndInsertMergeRows(List<Row> rows, List<NonDomEdge> nonDomEdges) {
        // nodeId → rowIndex マップ
        Map<NodeId, Integer> nodeRowIndex = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) instanceof Row.NodeRow nr) {
                nodeRowIndex.put(nr.node().id(), i);
            }
        }

        // ターゲット別にグループ化
        Map<NodeId, List<NonDomEdge>> edgesByTarget = new LinkedHashMap<>();
        for (NonDomEdge edge : nonDomEdges) {
            edgesByTarget.computeIfAbsent(edge.target(), k -> new ArrayList<>()).add(edge);
        }

        // グループ情報を構築
        List<GroupInfo> groups = new ArrayList<>();
        for (Map.Entry<NodeId, List<NonDomEdge>> entry : edgesByTarget.entrySet()) {
            NodeId target = entry.getKey();
            List<NonDomEdge> edges = entry.getValue();
            List<NodeId> sources =
                    edges.stream()
                            .map(NonDomEdge::source)
                            .sorted(Comparator.comparingInt(s -> nodeRowIndex.get(s)))
                            .toList();
            int startRow = sources.stream().mapToInt(s -> nodeRowIndex.get(s)).min().orElse(0);
            Integer endRowObj = nodeRowIndex.get(target);
            if (endRowObj == null) continue;
            int endRow = endRowObj;
            groups.add(new GroupInfo(target, sources, startRow, endRow));
        }
        groups.sort(Comparator.comparingInt(GroupInfo::startRow));

        // レーン割り当て（再利用あり）
        int[] laneEndRow = new int[groups.size()];
        Arrays.fill(laneEndRow, -1);
        int lc = 0;
        int[] groupLane = new int[groups.size()];

        for (int g = 0; g < groups.size(); g++) {
            GroupInfo group = groups.get(g);
            int lane = -1;
            for (int i = 0; i < lc; i++) {
                if (laneEndRow[i] < group.startRow()) {
                    lane = i;
                    break;
                }
            }
            if (lane == -1) {
                lane = lc++;
            }
            groupLane[g] = lane;
            laneEndRow[lane] = group.endRow();
        }

        // レーン範囲とアクション情報を構築
        // boolean[row][lane] — レーン再利用に対応するため行ごとに累積
        boolean[][] laneActive = new boolean[rows.size()][lc];
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
                int action = first ? 0 : 1; // 0=START, 1=JOIN
                nodeLaneActions
                        .computeIfAbsent(source, k -> new ArrayList<>())
                        .add(new int[] {lane, action});
                first = false;
            }
        }

        // 元の行のレーン文字を計算
        List<String> origLaneChars = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            StringBuilder sb = new StringBuilder();

            if (row instanceof Row.NodeRow nr) {
                NodeId nodeId = nr.node().id();
                List<int[]> actions = nodeLaneActions.getOrDefault(nodeId, List.of());
                for (int l = 0; l < lc; l++) {
                    char ch = computeNodeLaneChar(i, l, actions, laneActive);
                    sb.append(ch);
                }
            } else if (row instanceof Row.ConnectorRow || row instanceof Row.BlankRow) {
                for (int l = 0; l < lc; l++) {
                    sb.append(isLaneActiveAtRow(i, l, laneActive) ? '│' : ' ');
                }
            } else {
                for (int l = 0; l < lc; l++) {
                    sb.append(' ');
                }
            }
            origLaneChars.add(sb.toString());
        }

        // マージ行を挿入してファイナルリストを構築
        List<Row> finalRows = new ArrayList<>();
        List<String> finalLaneChars = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);

            // ターゲットノードの前にマージ行を挿入
            if (row instanceof Row.NodeRow nr && targetLaneMap.containsKey(nr.node().id())) {
                int targetLane = targetLaneMap.get(nr.node().id());

                Set<Integer> mergeActive = new TreeSet<>(nr.activeColumns());
                mergeActive.add(nr.column());

                int mergeCol = nr.isBranch() ? nr.column() - 1 : nr.column();
                finalRows.add(new Row.MergeRow(mergeCol, mergeActive));

                // マージ行のレーン文字
                StringBuilder mlc = new StringBuilder();
                for (int l = 0; l < lc; l++) {
                    if (l == targetLane) {
                        mlc.append('┘');
                    } else if (l < targetLane) {
                        mlc.append(isLaneActiveAtRow(i, l, laneActive) ? '┼' : '─');
                    } else {
                        mlc.append(isLaneActiveAtRow(i - 1, l, laneActive) ? '│' : ' ');
                    }
                }
                finalLaneChars.add(mlc.toString());
            }

            finalRows.add(row);
            finalLaneChars.add(origLaneChars.get(i));
        }

        return new LaneResult(finalRows, lc, finalLaneChars);
    }

    private char computeNodeLaneChar(
            int rowIndex, int lane, List<int[]> actions, boolean[][] laneActive) {
        for (int[] action : actions) {
            if (action[0] == lane) {
                return action[1] == 0 ? '┐' : '┤';
            }
        }
        if (isLaneActiveAtRow(rowIndex, lane, laneActive)) {
            return '│';
        }
        return ' ';
    }

    private boolean isLaneActiveAtRow(int rowIndex, int lane, boolean[][] laneActive) {
        if (rowIndex < 0 || rowIndex >= laneActive.length) return false;
        if (lane < 0 || lane >= laneActive[rowIndex].length) return false;
        return laneActive[rowIndex][lane];
    }

    // ========== ASCII レンダリング ==========

    private List<String> doRender(
            List<Row> renderRows,
            int laneCount,
            List<String> rowLaneChars,
            Map<NodeId, String> labels) {
        List<String> output = new ArrayList<>();
        int graphWidth = maxColumn > 0 ? 2 * maxColumn + 1 : 1;
        boolean hasLanes = laneCount > 0;

        for (int idx = 0; idx < renderRows.size(); idx++) {
            Row row = renderRows.get(idx);
            String laneStr = idx < rowLaneChars.size() ? rowLaneChars.get(idx) : "";

            switch (row) {
                case Row.BlankRow ignored -> {
                    if (hasLanes && !laneStr.isBlank()) {
                        String graphPart = " ".repeat(graphWidth);
                        String lanePart = buildLaneAreaForConnector(laneStr, laneCount);
                        output.add((graphPart + lanePart).stripTrailing());
                    } else {
                        output.add("");
                    }
                }
                case Row.ConnectorRow cr -> {
                    String graphPart =
                            buildConnectorLine(
                                    cr.column(), cr.activeColumns(), graphWidth, hasLanes);
                    String lanePart = buildLaneAreaForConnector(laneStr, laneCount);
                    output.add((graphPart + lanePart).stripTrailing());
                }
                case Row.MergeRow mr -> {
                    String graphPart = buildMergeLine(mr.column(), mr.activeColumns(), graphWidth);
                    String lanePart = buildLaneAreaForMerge(laneStr, laneCount);
                    output.add((graphPart + lanePart).stripTrailing());
                }
                case Row.NodeRow nr -> {
                    String graphPart =
                            buildNodeLine(
                                    nr.column(),
                                    nr.isBranch(),
                                    nr.isLastChild(),
                                    nr.activeColumns(),
                                    graphWidth,
                                    hasLanes);
                    String lanePart = buildLaneAreaForNode(laneStr, laneCount);
                    if (hasLaneConnection(laneStr)) {
                        graphPart = fillSpacesAfterNode(graphPart);
                    }
                    String label = labels.getOrDefault(nr.node().id(), "");
                    output.add((graphPart + lanePart + " " + label).stripTrailing());
                }
            }
        }

        return output;
    }

    private String buildConnectorLine(
            int col, Set<Integer> activeColumns, int graphWidth, boolean pad) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c <= maxColumn; c++) {
            if (c == col || activeColumns.contains(c)) {
                sb.append("│");
            } else {
                sb.append(" ");
            }
            if (c < maxColumn) {
                sb.append(" ");
            }
        }
        return pad ? padToWidth(sb, graphWidth) : sb.toString().stripTrailing();
    }

    private String buildNodeLine(
            int col,
            boolean isBranch,
            boolean isLastChild,
            Set<Integer> activeColumns,
            int graphWidth,
            boolean pad) {
        StringBuilder sb = new StringBuilder();

        if (isBranch) {
            for (int c = 0; c <= maxColumn; c++) {
                if (c < col - 1) {
                    sb.append(activeColumns.contains(c) ? "│" : " ");
                    sb.append(" ");
                } else if (c == col - 1) {
                    sb.append(isLastChild ? "└" : "├");
                    sb.append("─");
                } else if (c == col) {
                    sb.append("●");
                    if (c < maxColumn) sb.append(" ");
                } else {
                    sb.append(activeColumns.contains(c) ? "│" : " ");
                    if (c < maxColumn) sb.append(" ");
                }
            }
        } else {
            for (int c = 0; c <= maxColumn; c++) {
                if (c == col) {
                    sb.append("●");
                } else if (activeColumns.contains(c)) {
                    sb.append("│");
                } else {
                    sb.append(" ");
                }
                if (c < maxColumn) {
                    sb.append(" ");
                }
            }
        }

        return pad ? padToWidth(sb, graphWidth) : sb.toString().stripTrailing();
    }

    private String buildMergeLine(int col, Set<Integer> activeColumns, int graphWidth) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c <= maxColumn; c++) {
            if (c < col) {
                sb.append(activeColumns.contains(c) ? "│" : " ");
                sb.append(" ");
            } else if (c == col) {
                sb.append("├");
                if (c < maxColumn) sb.append("─");
            } else {
                sb.append("─");
                if (c < maxColumn) sb.append("─");
            }
        }
        return padToWidth(sb, graphWidth);
    }

    private String buildLaneAreaForNode(String laneStr, int laneCount) {
        if (laneStr.isEmpty() || laneCount == 0) return "";

        int maxConnectedLane = -1;
        for (int l = 0; l < laneStr.length(); l++) {
            char ch = laneStr.charAt(l);
            if (ch == '┐' || ch == '┤') {
                maxConnectedLane = l;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(maxConnectedLane >= 0 ? '─' : ' ');

        for (int l = 0; l < laneStr.length(); l++) {
            char ch = laneStr.charAt(l);
            if (maxConnectedLane >= 0 && ch == ' ' && l <= maxConnectedLane) {
                sb.append('─');
            } else {
                sb.append(ch);
            }
        }

        return sb.toString();
    }

    private String buildLaneAreaForConnector(String laneStr, int laneCount) {
        if (laneStr.isEmpty() || laneCount == 0) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(' ');
        sb.append(laneStr);
        return sb.toString();
    }

    private String buildLaneAreaForMerge(String laneStr, int laneCount) {
        if (laneStr.isEmpty() || laneCount == 0) return "";

        StringBuilder sb = new StringBuilder();
        sb.append('─');
        sb.append(laneStr);
        return sb.toString();
    }

    private boolean hasLaneConnection(String laneStr) {
        for (int i = 0; i < laneStr.length(); i++) {
            char ch = laneStr.charAt(i);
            if (ch == '┐' || ch == '┤') return true;
        }
        return false;
    }

    private String fillSpacesAfterNode(String graphPart) {
        int nodePos = graphPart.indexOf('●');
        if (nodePos < 0) return graphPart;
        char[] chars = graphPart.toCharArray();
        for (int i = nodePos + 1; i < chars.length; i++) {
            if (chars[i] == ' ') {
                chars[i] = '─';
            }
        }
        return new String(chars);
    }

    private String padToWidth(StringBuilder sb, int width) {
        while (sb.length() < width) {
            sb.append(" ");
        }
        return sb.toString();
    }

    // ========== ユーティリティ ==========

    private DominatorTree dt() {
        if (dt == null) throw new IllegalStateException("layout() が呼ばれていません");
        return dt;
    }
}
