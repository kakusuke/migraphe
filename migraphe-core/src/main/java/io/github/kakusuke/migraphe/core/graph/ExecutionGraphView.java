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

    private sealed interface RenderRow {
        record NodeRow(MigrationNode node, int column, boolean isBranch, Set<Integer> activeColumns)
                implements RenderRow {}

        record ConnectorRow(int column, Set<Integer> activeColumns) implements RenderRow {}

        record BlankRow() implements RenderRow {}
    }

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
        buildDominatorTreeView(sortedNodes, infos, rows);
        this.lineInfos = List.copyOf(infos);
        this.renderRows = List.copyOf(rows);

        int mc = 0;
        for (NodeLineInfo info : lineInfos) {
            mc = Math.max(mc, info.column());
        }
        this.maxColumn = mc;
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
            List<RenderRow> outRows) {
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

            boolean first = true;
            // branch subtree を先に描画
            for (NodeId child : virtualChildren) {
                if (!child.equals(virtualTrunk)) {
                    if (!first) {
                        addSubgraphSeparator(child, outRows, outLineInfos);
                    }
                    emitSubtree(
                            child,
                            0,
                            false,
                            Set.of(),
                            domChildren,
                            trunkChild,
                            outLineInfos,
                            outRows);
                    first = false;
                }
            }
            // trunk を最後に描画
            if (virtualTrunk != null) {
                if (!first) {
                    addSubgraphSeparator(virtualTrunk, outRows, outLineInfos);
                }
                emitSubtree(
                        virtualTrunk,
                        0,
                        false,
                        Set.of(),
                        domChildren,
                        trunkChild,
                        outLineInfos,
                        outRows);
            }
        } else {
            emitSubtree(
                    roots.get(0),
                    0,
                    false,
                    Set.of(),
                    domChildren,
                    trunkChild,
                    outLineInfos,
                    outRows);
        }
    }

    private void addSubgraphSeparator(
            NodeId nextRoot, List<RenderRow> outRows, List<NodeLineInfo> prevInfos) {
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
            Set<Integer> activeColumns,
            Map<NodeId, List<NodeId>> domChildren,
            Map<NodeId, @Nullable NodeId> trunkChild,
            List<NodeLineInfo> outLineInfos,
            List<RenderRow> outRows) {
        MigrationNode node = nodeMap.get(nodeId);
        if (node == null) return;

        outLineInfos.add(new NodeLineInfo(node, column));
        outRows.add(new RenderRow.NodeRow(node, column, isBranch, new TreeSet<>(activeColumns)));

        List<NodeId> children = domChildren.getOrDefault(nodeId, List.of());
        @Nullable NodeId trunk = trunkChild.get(nodeId);
        List<NodeId> branches = new ArrayList<>();
        for (NodeId child : children) {
            if (!child.equals(trunk)) branches.add(child);
        }

        if (!branches.isEmpty()) {
            // branch サブツリー描画中は、現在のカラムが active（trunk が続くことを示す）
            Set<Integer> branchActive = new TreeSet<>(activeColumns);
            branchActive.add(column);

            for (NodeId branch : branches) {
                emitSubtree(
                        branch,
                        column + 1,
                        true,
                        branchActive,
                        domChildren,
                        trunkChild,
                        outLineInfos,
                        outRows);
            }
        }

        if (trunk != null) {
            if (branches.isEmpty()) {
                // 純粋なチェーン → コネクタ行を挿入
                outRows.add(new RenderRow.ConnectorRow(column, new TreeSet<>(activeColumns)));
            }
            emitSubtree(
                    trunk,
                    column,
                    false,
                    activeColumns,
                    domChildren,
                    trunkChild,
                    outLineInfos,
                    outRows);
        }
    }

    // ========== ASCII レンダリング ==========

    private List<String> doRender(Map<NodeId, String> labels) {
        List<String> output = new ArrayList<>();
        int graphWidth = maxColumn > 0 ? 2 * maxColumn + 1 : 1;

        for (RenderRow row : renderRows) {
            switch (row) {
                case RenderRow.BlankRow ignored -> output.add("");
                case RenderRow.ConnectorRow cr ->
                        output.add(buildConnectorLine(cr.column(), cr.activeColumns(), graphWidth));
                case RenderRow.NodeRow nr -> {
                    String graphPart =
                            buildNodeLine(
                                    nr.column(), nr.isBranch(), nr.activeColumns(), graphWidth);
                    String label = labels.getOrDefault(nr.node().id(), "");
                    output.add((graphPart + " " + label).stripTrailing());
                }
            }
        }

        return output;
    }

    private String buildConnectorLine(int col, Set<Integer> activeColumns, int graphWidth) {
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
        return padToWidth(sb, graphWidth).stripTrailing();
    }

    private String buildNodeLine(
            int col, boolean isBranch, Set<Integer> activeColumns, int graphWidth) {
        StringBuilder sb = new StringBuilder();

        if (isBranch) {
            for (int c = 0; c <= maxColumn; c++) {
                if (c < col - 1) {
                    sb.append(activeColumns.contains(c) ? "│" : " ");
                    sb.append(" ");
                } else if (c == col - 1) {
                    // branch は常に ├（trunk が後に続くため）
                    sb.append("├");
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

        return padToWidth(sb, graphWidth).stripTrailing();
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
