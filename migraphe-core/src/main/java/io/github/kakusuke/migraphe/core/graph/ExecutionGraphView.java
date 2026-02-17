package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.*;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * DAG の実行グラフをテキスト表現するクラス。
 *
 * <p>支配木 (Dominator Tree) ベースの描画方式。
 *
 * <ul>
 *   <li>トポロジカルソートでノードを並べる
 *   <li>支配木を構築し、trunk/branch を決定
 *   <li>DFS で支配木を走査し、ASCII プレフィックスを生成
 *   <li>非支配木辺をレーンとして右端に描画
 * </ul>
 */
public final class ExecutionGraphView {

    private static final NodeId VIRTUAL_ROOT = NodeId.of("__virtual_root__");

    private final Map<NodeId, MigrationNode> nodeMap;
    private final Map<NodeId, List<NodeId>> childrenOf;
    private final Map<NodeId, List<NodeId>> parentsOf;
    private final List<NodeLineInfo> lineInfos;
    private final List<RenderRow> renderRows;
    private final int maxColumn;
    private final int laneCount;
    private final List<String> rowLaneChars;

    private sealed interface RenderRow {
        record NodeRow(
                MigrationNode node,
                int column,
                boolean isBranch,
                boolean isLastChild,
                Set<Integer> activeColumns)
                implements RenderRow {}

        record ConnectorRow(int column, Set<Integer> activeColumns) implements RenderRow {}

        record MergeRow(int column, Set<Integer> activeColumns) implements RenderRow {}

        record BlankRow() implements RenderRow {}
    }

    private record NonDomEdge(NodeId source, NodeId target) {}

    private record BranchClassification(List<NodeId> preTrunk, List<NodeId> postTrunk) {}

    /**
     * コンストラクタ。
     *
     * @param sortedNodes ソート済みノードリスト
     * @param reversed true の場合、逆順モード（DOWN用）。依存関係を逆に解釈する。
     */
    public ExecutionGraphView(List<MigrationNode> sortedNodes, boolean reversed) {
        this.nodeMap = new LinkedHashMap<>();

        Map<NodeId, Set<NodeId>> dependents = new LinkedHashMap<>();

        for (MigrationNode node : sortedNodes) {
            nodeMap.put(node.id(), node);
        }
        for (MigrationNode node : sortedNodes) {
            for (NodeId dep : node.dependencies()) {
                dependents.computeIfAbsent(dep, k -> new LinkedHashSet<>()).add(node.id());
            }
        }

        // 推移的簡約済みの子ノードを計算
        this.childrenOf = new LinkedHashMap<>();
        for (MigrationNode node : sortedNodes) {
            Set<NodeId> raw =
                    reversed ? node.dependencies() : dependents.getOrDefault(node.id(), Set.of());
            Set<NodeId> reduced = removeTransitive(raw);
            List<NodeId> ordered =
                    sortedNodes.stream().map(MigrationNode::id).filter(reduced::contains).toList();
            childrenOf.put(node.id(), ordered);
        }

        // 推移的簡約済みの親ノードを計算
        this.parentsOf = new LinkedHashMap<>();
        for (MigrationNode node : sortedNodes) {
            Set<NodeId> raw =
                    reversed ? dependents.getOrDefault(node.id(), Set.of()) : node.dependencies();
            Set<NodeId> reduced = removeTransitiveParents(raw);
            List<NodeId> ordered =
                    sortedNodes.stream().map(MigrationNode::id).filter(reduced::contains).toList();
            parentsOf.put(node.id(), ordered);
        }

        List<NodeLineInfo> infos = new ArrayList<>();
        List<RenderRow> rows = new ArrayList<>();
        List<NonDomEdge> nonDomEdges = new ArrayList<>();
        buildDominatorTreeView(sortedNodes, infos, rows, nonDomEdges);
        this.lineInfos = List.copyOf(infos);

        int mc = 0;
        for (NodeLineInfo info : lineInfos) {
            mc = Math.max(mc, info.column());
        }
        this.maxColumn = mc;

        // レーン割り当て + マージ行挿入
        if (nonDomEdges.isEmpty()) {
            this.renderRows = List.copyOf(rows);
            this.laneCount = 0;
            this.rowLaneChars = Collections.nCopies(rows.size(), "");
        } else {
            LaneResult laneResult = assignLanesAndInsertMergeRows(rows, nonDomEdges);
            this.renderRows = List.copyOf(laneResult.rows);
            this.laneCount = laneResult.laneCount;
            this.rowLaneChars = List.copyOf(laneResult.laneChars);
        }
    }

    /** 各ノードの行情報リストを取得する。 */
    public List<NodeLineInfo> lines() {
        return lineInfos;
    }

    /**
     * 各ノードのラベル付き行をリストとして生成する。
     *
     * @param labelFn 各ノードに対するフルラベルを返す関数
     * @return 表示行のリスト
     */
    public List<String> renderLines(Function<MigrationNode, String> labelFn) {
        Map<NodeId, String> labels = new LinkedHashMap<>();
        for (NodeLineInfo info : lineInfos) {
            labels.put(info.node().id(), labelFn.apply(info.node()));
        }
        return doRender(labels);
    }

    /** プレーンテキストとしてグラフ全体を出力する（色なし）。 */
    @Override
    public String toString() {
        List<String> rendered =
                renderLines(node -> "[ ] " + node.id().value() + " - " + node.name());
        StringBuilder sb = new StringBuilder();
        for (String line : rendered) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    // ========== 支配木構築 + DFS レンダリング ==========

    private void buildDominatorTreeView(
            List<MigrationNode> sortedNodes,
            List<NodeLineInfo> outLineInfos,
            List<RenderRow> outRows,
            List<NonDomEdge> outNonDomEdges) {
        if (sortedNodes.isEmpty()) return;

        // Step 1: 支配木の構築
        List<NodeId> topoOrder = sortedNodes.stream().map(MigrationNode::id).toList();
        List<NodeId> roots = new ArrayList<>();
        for (NodeId id : topoOrder) {
            if (parents(id).isEmpty()) {
                roots.add(id);
            }
        }

        // idom の計算
        Map<NodeId, NodeId> idom = new LinkedHashMap<>();
        boolean hasVirtualRoot = roots.size() > 1;

        if (hasVirtualRoot) {
            for (NodeId root : roots) {
                idom.put(root, VIRTUAL_ROOT);
            }
        }

        for (NodeId nodeId : topoOrder) {
            if (roots.contains(nodeId)) continue;
            List<NodeId> p = parents(nodeId);
            if (p.size() == 1) {
                idom.put(nodeId, p.get(0));
            } else {
                // 複数親: LCA を計算
                NodeId lca = p.get(0);
                for (int i = 1; i < p.size(); i++) {
                    lca = lcaInDomTree(lca, p.get(i), idom);
                }
                idom.put(nodeId, lca);
            }
        }

        // 非支配木辺の特定
        outNonDomEdges.addAll(findNonDomEdges(idom));

        // 支配木の子リストを構築
        Map<NodeId, List<NodeId>> domChildren = new LinkedHashMap<>();
        for (Map.Entry<NodeId, NodeId> entry : idom.entrySet()) {
            domChildren
                    .computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(entry.getKey());
        }

        // Step 2: Trunk 選択
        Map<NodeId, Integer> subtreeDepth = new LinkedHashMap<>();
        computeSubtreeDepth(
                hasVirtualRoot ? VIRTUAL_ROOT : roots.get(0), domChildren, subtreeDepth);

        Map<NodeId, @Nullable NodeId> trunkChild = new LinkedHashMap<>();
        computeTrunkChild(
                hasVirtualRoot ? VIRTUAL_ROOT : roots.get(0),
                domChildren,
                subtreeDepth,
                trunkChild,
                topoOrder);

        // Step 3: DFS レンダリング
        if (hasVirtualRoot) {
            List<NodeId> virtualChildren = domChildren.getOrDefault(VIRTUAL_ROOT, List.of());
            @Nullable NodeId virtualTrunk = trunkChild.get(VIRTUAL_ROOT);

            List<NodeId> virtualBranches = new ArrayList<>();
            for (NodeId child : virtualChildren) {
                if (!child.equals(virtualTrunk)) virtualBranches.add(child);
            }

            BranchClassification classification =
                    classifyBranches(virtualTrunk, virtualBranches, domChildren, outNonDomEdges);

            boolean first = true;
            // Pre-trunk branches
            for (NodeId child : classification.preTrunk()) {
                if (!first) {
                    addSubgraphSeparator(outRows, outLineInfos);
                }
                emitSubtree(
                        child,
                        0,
                        false,
                        false,
                        Set.of(),
                        domChildren,
                        trunkChild,
                        outNonDomEdges,
                        outLineInfos,
                        outRows);
                first = false;
            }
            // Trunk
            if (virtualTrunk != null) {
                if (!first) {
                    addSubgraphSeparator(outRows, outLineInfos);
                }
                emitSubtree(
                        virtualTrunk,
                        0,
                        false,
                        false,
                        Set.of(),
                        domChildren,
                        trunkChild,
                        outNonDomEdges,
                        outLineInfos,
                        outRows);
                first = false;
            }
            // Post-trunk branches
            for (NodeId child : classification.postTrunk()) {
                if (!first) {
                    addSubgraphSeparator(outRows, outLineInfos);
                }
                emitSubtree(
                        child,
                        0,
                        false,
                        false,
                        Set.of(),
                        domChildren,
                        trunkChild,
                        outNonDomEdges,
                        outLineInfos,
                        outRows);
                first = false;
            }
        } else {
            emitSubtree(
                    roots.get(0),
                    0,
                    false,
                    false,
                    Set.of(),
                    domChildren,
                    trunkChild,
                    outNonDomEdges,
                    outLineInfos,
                    outRows);
        }
    }

    private void addSubgraphSeparator(List<RenderRow> outRows, List<NodeLineInfo> prevInfos) {
        // 前のサブグラフに可視構造があれば空行
        if (!prevInfos.isEmpty()) {
            NodeLineInfo last = prevInfos.get(prevInfos.size() - 1);
            @Nullable NodeId prevRoot = subgraphRoot(last.node().id());
            if (prevRoot != null && hasVisibleStructure(prevRoot)) {
                outRows.add(new RenderRow.BlankRow());
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
            Map<NodeId, @Nullable NodeId> trunkChild,
            List<NonDomEdge> nonDomEdges,
            List<NodeLineInfo> outLineInfos,
            List<RenderRow> outRows) {
        MigrationNode node = nodeMap.get(nodeId);
        if (node == null) return;

        outLineInfos.add(new NodeLineInfo(node, column));
        outRows.add(
                new RenderRow.NodeRow(
                        node, column, isBranch, isLastChild, new TreeSet<>(activeColumns)));

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
                emitSubtree(
                        branch,
                        column + 1,
                        true,
                        false,
                        branchActive,
                        domChildren,
                        trunkChild,
                        nonDomEdges,
                        outLineInfos,
                        outRows);
            }
        }

        // Trunk
        if (trunk != null) {
            if (preTrunk.isEmpty() && !hasPostTrunk) {
                // 純粋なチェーン → コネクタ行を挿入
                outRows.add(new RenderRow.ConnectorRow(column, new TreeSet<>(activeColumns)));
            } else if (preTrunk.isEmpty()) {
                // post-trunk のみ → コネクタ行を挿入（trunk との間に隙間がないように）
                outRows.add(new RenderRow.ConnectorRow(column, new TreeSet<>(activeColumns)));
            }

            // post-trunk がある場合、trunk サブツリー中も column を active に保つ
            Set<Integer> trunkActive;
            if (hasPostTrunk) {
                trunkActive = new TreeSet<>(activeColumns);
                trunkActive.add(column);
            } else {
                trunkActive = activeColumns;
            }

            emitSubtree(
                    trunk,
                    column,
                    false,
                    false,
                    trunkActive,
                    domChildren,
                    trunkChild,
                    nonDomEdges,
                    outLineInfos,
                    outRows);
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
                        trunkChild,
                        nonDomEdges,
                        outLineInfos,
                        outRows);
            }
        }
    }

    // ========== 非支配木辺 ==========

    private List<NonDomEdge> findNonDomEdges(Map<NodeId, NodeId> idom) {
        List<NonDomEdge> edges = new ArrayList<>();
        for (Map.Entry<NodeId, List<NodeId>> entry : childrenOf.entrySet()) {
            NodeId parent = entry.getKey();
            for (NodeId child : entry.getValue()) {
                NodeId childIdom = idom.get(child);
                if (childIdom != null && !childIdom.equals(parent)) {
                    edges.add(new NonDomEdge(parent, child));
                }
            }
        }
        return edges;
    }

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

    private record LaneResult(List<RenderRow> rows, int laneCount, List<String> laneChars) {}

    private LaneResult assignLanesAndInsertMergeRows(
            List<RenderRow> initialRows, List<NonDomEdge> nonDomEdges) {
        // nodeId → rowIndex マップ
        Map<NodeId, Integer> nodeRowIndex = new LinkedHashMap<>();
        for (int i = 0; i < initialRows.size(); i++) {
            if (initialRows.get(i) instanceof RenderRow.NodeRow nr) {
                nodeRowIndex.put(nr.node().id(), i);
            }
        }

        // ターゲット別にグループ化
        Map<NodeId, List<NonDomEdge>> edgesByTarget = new LinkedHashMap<>();
        for (NonDomEdge edge : nonDomEdges) {
            edgesByTarget.computeIfAbsent(edge.target(), k -> new ArrayList<>()).add(edge);
        }

        // グループ情報を構築
        record GroupInfo(NodeId target, List<NodeId> sources, int startRow, int endRow) {}
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
        int[][] laneRange = new int[lc][2];
        Map<NodeId, List<int[]>> nodeLaneActions = new LinkedHashMap<>();
        Map<NodeId, Integer> targetLaneMap = new LinkedHashMap<>();

        for (int g = 0; g < groups.size(); g++) {
            GroupInfo group = groups.get(g);
            int lane = groupLane[g];
            laneRange[lane] = new int[] {group.startRow(), group.endRow()};
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
        for (int i = 0; i < initialRows.size(); i++) {
            RenderRow row = initialRows.get(i);
            StringBuilder sb = new StringBuilder();

            if (row instanceof RenderRow.NodeRow nr) {
                NodeId nodeId = nr.node().id();
                List<int[]> actions = nodeLaneActions.getOrDefault(nodeId, List.of());
                for (int l = 0; l < lc; l++) {
                    char ch = computeNodeLaneChar(i, l, actions, laneRange);
                    sb.append(ch);
                }
            } else if (row instanceof RenderRow.ConnectorRow) {
                for (int l = 0; l < lc; l++) {
                    sb.append(isLaneActiveAtRow(i, l, laneRange) ? '│' : ' ');
                }
            } else if (row instanceof RenderRow.BlankRow) {
                for (int l = 0; l < lc; l++) {
                    sb.append(isLaneActiveAtRow(i, l, laneRange) ? '│' : ' ');
                }
            } else {
                for (int l = 0; l < lc; l++) {
                    sb.append(' ');
                }
            }
            origLaneChars.add(sb.toString());
        }

        // マージ行を挿入してファイナルリストを構築
        List<RenderRow> finalRows = new ArrayList<>();
        List<String> finalLaneChars = new ArrayList<>();

        for (int i = 0; i < initialRows.size(); i++) {
            RenderRow row = initialRows.get(i);

            // ターゲットノードの前にマージ行を挿入
            if (row instanceof RenderRow.NodeRow nr && targetLaneMap.containsKey(nr.node().id())) {
                int targetLane = targetLaneMap.get(nr.node().id());

                Set<Integer> mergeActive = new TreeSet<>(nr.activeColumns());
                mergeActive.add(nr.column());

                finalRows.add(new RenderRow.MergeRow(nr.column(), mergeActive));

                // マージ行のレーン文字
                StringBuilder mlc = new StringBuilder();
                for (int l = 0; l < lc; l++) {
                    if (l == targetLane) {
                        mlc.append('┘');
                    } else if (l < targetLane) {
                        mlc.append(isLaneActiveAtRow(i, l, laneRange) ? '┼' : '─');
                    } else {
                        mlc.append(isLaneActiveAtRow(i, l, laneRange) ? '│' : ' ');
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
            int rowIndex, int lane, List<int[]> actions, int[][] laneRange) {
        // このノードがこのレーンで START/JOIN するか
        for (int[] action : actions) {
            if (action[0] == lane) {
                return action[1] == 0 ? '┐' : '┤';
            }
        }
        // レーンがアクティブか（通過中）
        if (isLaneActiveAtRow(rowIndex, lane, laneRange)) {
            return '│';
        }
        return ' ';
    }

    private boolean isLaneActiveAtRow(int rowIndex, int lane, int[][] laneRange) {
        if (lane >= laneRange.length) return false;
        return rowIndex >= laneRange[lane][0] && rowIndex < laneRange[lane][1];
    }

    // ========== ASCII レンダリング ==========

    private List<String> doRender(Map<NodeId, String> labels) {
        List<String> output = new ArrayList<>();
        int graphWidth = maxColumn > 0 ? 2 * maxColumn + 1 : 1;
        boolean hasLanes = laneCount > 0;

        for (int idx = 0; idx < renderRows.size(); idx++) {
            RenderRow row = renderRows.get(idx);
            String laneStr = idx < rowLaneChars.size() ? rowLaneChars.get(idx) : "";

            switch (row) {
                case RenderRow.BlankRow ignored -> {
                    if (hasLanes && !laneStr.isBlank()) {
                        String graphPart = " ".repeat(graphWidth);
                        String lanePart = buildLaneAreaForConnector(laneStr);
                        output.add((graphPart + lanePart).stripTrailing());
                    } else {
                        output.add("");
                    }
                }
                case RenderRow.ConnectorRow cr -> {
                    String graphPart =
                            buildConnectorLine(
                                    cr.column(), cr.activeColumns(), graphWidth, hasLanes);
                    String lanePart = buildLaneAreaForConnector(laneStr);
                    output.add((graphPart + lanePart).stripTrailing());
                }
                case RenderRow.MergeRow mr -> {
                    String graphPart = buildMergeLine(mr.column(), mr.activeColumns(), graphWidth);
                    String lanePart = buildLaneAreaForMerge(laneStr);
                    output.add((graphPart + lanePart).stripTrailing());
                }
                case RenderRow.NodeRow nr -> {
                    String graphPart =
                            buildNodeLine(
                                    nr.column(),
                                    nr.isBranch(),
                                    nr.isLastChild(),
                                    nr.activeColumns(),
                                    graphWidth,
                                    hasLanes);
                    String lanePart = buildLaneAreaForNode(laneStr);
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

    private String buildLaneAreaForNode(String laneStr) {
        if (laneStr.isEmpty() || laneCount == 0) return "";

        // ノードがレーンに接続するか判定
        int maxConnectedLane = -1;
        for (int l = 0; l < laneStr.length(); l++) {
            char ch = laneStr.charAt(l);
            if (ch == '┐' || ch == '┤') {
                maxConnectedLane = l;
            }
        }

        StringBuilder sb = new StringBuilder();
        // コネクタ文字
        sb.append(maxConnectedLane >= 0 ? '─' : ' ');

        for (int l = 0; l < laneStr.length(); l++) {
            char ch = laneStr.charAt(l);
            if (maxConnectedLane >= 0 && ch == ' ' && l <= maxConnectedLane) {
                // 水平線の途中：空きレーンは ─ で埋める
                sb.append('─');
            } else {
                sb.append(ch);
            }
        }

        return sb.toString();
    }

    private String buildLaneAreaForConnector(String laneStr) {
        if (laneStr.isEmpty() || laneCount == 0) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(' '); // コネクタとレーンの間
        sb.append(laneStr);
        return sb.toString();
    }

    private String buildLaneAreaForMerge(String laneStr) {
        if (laneStr.isEmpty() || laneCount == 0) return "";

        StringBuilder sb = new StringBuilder();
        sb.append('─'); // マージ行のコネクタ
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

    // ========== 支配木ユーティリティ ==========

    private NodeId lcaInDomTree(NodeId a, NodeId b, Map<NodeId, NodeId> idom) {
        Set<NodeId> ancestorsOfA = new LinkedHashSet<>();
        NodeId current = a;
        ancestorsOfA.add(current);
        while (idom.containsKey(current)) {
            current = idom.get(current);
            ancestorsOfA.add(current);
        }

        current = b;
        while (!ancestorsOfA.contains(current)) {
            if (!idom.containsKey(current)) break;
            current = idom.get(current);
        }
        return current;
    }

    private int computeSubtreeDepth(
            NodeId nodeId,
            Map<NodeId, List<NodeId>> domChildren,
            Map<NodeId, Integer> subtreeDepth) {
        List<NodeId> children = domChildren.getOrDefault(nodeId, List.of());
        if (children.isEmpty()) {
            subtreeDepth.put(nodeId, 0);
            return 0;
        }
        int maxDepth = 0;
        for (NodeId child : children) {
            int d = computeSubtreeDepth(child, domChildren, subtreeDepth);
            maxDepth = Math.max(maxDepth, d);
        }
        int depth = maxDepth + 1;
        subtreeDepth.put(nodeId, depth);
        return depth;
    }

    private void computeTrunkChild(
            NodeId nodeId,
            Map<NodeId, List<NodeId>> domChildren,
            Map<NodeId, Integer> subtreeDepth,
            Map<NodeId, @Nullable NodeId> trunkChild,
            List<NodeId> topoOrder) {
        List<NodeId> children = domChildren.getOrDefault(nodeId, List.of());
        if (children.isEmpty()) {
            trunkChild.put(nodeId, null);
        } else if (children.size() == 1) {
            trunkChild.put(nodeId, children.get(0));
        } else {
            // サブツリー深度が最大の子を trunk とする。タイブレーク: トポロジカル順で最後の子
            int maxDepth = -1;
            @Nullable NodeId best = null;
            for (NodeId child : children) {
                int d = subtreeDepth.getOrDefault(child, 0);
                int childTopoIdx = topoOrder.indexOf(child);
                if (d > maxDepth
                        || (d == maxDepth
                                && best != null
                                && childTopoIdx > topoOrder.indexOf(best))) {
                    maxDepth = d;
                    best = child;
                }
            }
            trunkChild.put(nodeId, best);
        }

        for (NodeId child : children) {
            computeTrunkChild(child, domChildren, subtreeDepth, trunkChild, topoOrder);
        }
    }

    // ========== DAG ユーティリティ ==========

    private List<NodeId> parents(NodeId nodeId) {
        return parentsOf.getOrDefault(nodeId, List.of());
    }

    private List<NodeId> children(NodeId nodeId) {
        return childrenOf.getOrDefault(nodeId, List.of());
    }

    private boolean hasVisibleStructure(NodeId nodeId) {
        return !children(nodeId).isEmpty();
    }

    private @Nullable NodeId subgraphRoot(NodeId nodeId) {
        List<NodeId> p = parents(nodeId);
        if (p.isEmpty()) return nodeId;
        for (NodeId parentId : p) {
            if (nodeMap.containsKey(parentId)) {
                return subgraphRoot(parentId);
            }
        }
        return nodeId;
    }

    private Set<NodeId> removeTransitive(Set<NodeId> ids) {
        if (ids.size() <= 1) return ids;
        Set<NodeId> result = new HashSet<>(ids);
        for (NodeId id : ids) {
            if (isReachableViaOthers(id, ids)) {
                result.remove(id);
            }
        }
        return result;
    }

    private Set<NodeId> removeTransitiveParents(Set<NodeId> parentIds) {
        if (parentIds.size() <= 1) return parentIds;
        Set<NodeId> result = new HashSet<>(parentIds);
        for (NodeId p : parentIds) {
            for (NodeId other : parentIds) {
                if (other.equals(p)) continue;
                // p が other に到達可能 → p は other の祖先 → p を除去（遠い方を消す）
                if (canReachDown(p, other, new HashSet<>())) {
                    result.remove(p);
                    break;
                }
            }
        }
        return result;
    }

    private boolean canReachDown(NodeId from, NodeId target, Set<NodeId> visited) {
        if (from.equals(target)) return true;
        if (!visited.add(from)) return false;
        for (NodeId child : childrenOf.getOrDefault(from, List.of())) {
            if (canReachDown(child, target, visited)) return true;
        }
        return false;
    }

    private boolean isReachableViaOthers(NodeId target, Set<NodeId> all) {
        for (NodeId other : all) {
            if (other.equals(target)) continue;
            if (isAncestor(other, target, new HashSet<>())) return true;
        }
        return false;
    }

    private boolean isAncestor(NodeId ancestor, NodeId descendant, Set<NodeId> visited) {
        if (visited.contains(descendant)) return false;
        visited.add(descendant);
        MigrationNode node = nodeMap.get(descendant);
        if (node == null) return false;
        Set<NodeId> p = node.dependencies();
        if (p.contains(ancestor)) return true;
        for (NodeId pid : p) {
            if (isAncestor(ancestor, pid, visited)) return true;
        }
        return false;
    }
}
