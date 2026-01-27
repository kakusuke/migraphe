package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.*;
import org.jspecify.annotations.Nullable;

/**
 * DAG の実行グラフをテキスト表現するクラス。
 *
 * <p>git log --graph 風の ASCII 表示を生成する。
 */
public final class ExecutionGraphView {

    private final List<MigrationNode> sortedNodes;
    private final Map<NodeId, MigrationNode> nodeMap;
    private final Map<NodeId, Set<NodeId>> dependents;
    private final boolean reversed;
    private final List<NodeLineInfo> lines;

    /** カラムの状態を管理するクラス */
    private static class ColumnState {
        @Nullable NodeId occupiedBy; // 現在のノード
        Set<NodeId> pendingChildren; // まだ処理されていない子ノード

        ColumnState() {
            this.occupiedBy = null;
            this.pendingChildren = new HashSet<>();
        }

        boolean isActive() {
            return occupiedBy != null || !pendingChildren.isEmpty();
        }

        void clear() {
            this.occupiedBy = null;
            this.pendingChildren.clear();
        }
    }

    /**
     * コンストラクタ。
     *
     * @param sortedNodes ソート済みノードリスト
     * @param reversed true の場合、逆順モード（DOWN用）。依存関係を逆に解釈する。
     */
    public ExecutionGraphView(List<MigrationNode> sortedNodes, boolean reversed) {
        this.sortedNodes = List.copyOf(sortedNodes);
        this.nodeMap = new HashMap<>();
        this.dependents = new HashMap<>();
        this.reversed = reversed;

        for (MigrationNode node : sortedNodes) {
            nodeMap.put(node.id(), node);
        }

        for (MigrationNode node : sortedNodes) {
            for (NodeId dep : node.dependencies()) {
                dependents.computeIfAbsent(dep, k -> new HashSet<>()).add(node.id());
            }
        }

        this.lines = render();
    }

    /** 各ノードの行情報リストを取得する。 */
    public List<NodeLineInfo> lines() {
        return lines;
    }

    /** プレーンテキストとしてグラフ全体を出力する（色なし）。 */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (NodeLineInfo info : lines) {
            if (info.mergeLine() != null) {
                sb.append(info.mergeLine()).append("\n");
            }
            sb.append(info.toPlainText("[ ]")).append("\n");
            if (info.branchLine() != null) {
                sb.append(info.branchLine()).append("\n");
            }
            if (info.connectorLine() != null) {
                sb.append(info.connectorLine()).append("\n");
            }
        }
        return sb.toString();
    }

    private List<NodeLineInfo> render() {
        List<NodeLineInfo> result = new ArrayList<>();
        List<ColumnState> columns = new ArrayList<>();

        for (int i = 0; i < sortedNodes.size(); i++) {
            MigrationNode node = sortedNodes.get(i);
            boolean isLast = (i == sortedNodes.size() - 1);

            Set<NodeId> children = getChildren(node);

            // 自分が pendingChildren に登録されているカラムを探す（親のカラム）
            List<Integer> parentCols = new ArrayList<>();
            for (int col = 0; col < columns.size(); col++) {
                ColumnState state = columns.get(col);
                if (state.pendingChildren.contains(node.id())) {
                    parentCols.add(col);
                }
            }
            Collections.sort(parentCols);

            // ノードのカラムを決定
            int nodeCol;
            if (!parentCols.isEmpty()) {
                nodeCol = parentCols.get(0);
            } else {
                nodeCol = findOrCreateColumn(columns);
            }

            String branchLine = null;
            String mergeLine = null;

            // マージ処理（複数の親がいる場合）
            if (parentCols.size() > 1) {
                mergeLine = buildMergeLine(columns, parentCols, nodeCol);
            }

            // 親のカラムから自分を削除し、空になったらクリア
            for (int col : parentCols) {
                ColumnState state = columns.get(col);
                state.pendingChildren.remove(node.id());
                if (state.pendingChildren.isEmpty()) {
                    state.clear();
                }
            }

            // ノード行を描画
            String graphPrefix = buildNodeLine(columns, nodeCol);

            // 子がいる場合、このノードでカラムを占有
            if (!children.isEmpty()) {
                ensureColumnExists(columns, nodeCol);
                ColumnState state = columns.get(nodeCol);
                state.occupiedBy = node.id();

                List<NodeId> childList = new ArrayList<>(children);

                // 分岐処理（複数の子がいる場合）
                if (children.size() > 1) {
                    List<Integer> childCols = new ArrayList<>();
                    childCols.add(nodeCol);

                    // 最初の子はメインカラムに残る
                    state.pendingChildren.add(childList.get(0));

                    // 残りの子は新しいカラムに分岐
                    for (int c = 1; c < childList.size(); c++) {
                        int newCol = findOrCreateColumn(columns);
                        ensureColumnExists(columns, newCol);
                        ColumnState newState = columns.get(newCol);
                        newState.occupiedBy = node.id();
                        newState.pendingChildren.add(childList.get(c));
                        childCols.add(newCol);
                    }

                    branchLine = buildBranchLine(columns, nodeCol, childCols);
                } else {
                    // 単一の子
                    state.pendingChildren.add(childList.get(0));
                }
            }

            String connectorLine = null;
            if (!isLast && hasActiveColumns(columns)) {
                connectorLine = buildConnectorLine(columns);
            }

            result.add(new NodeLineInfo(node, graphPrefix, mergeLine, branchLine, connectorLine));
        }

        return result;
    }

    private Set<NodeId> getChildren(MigrationNode node) {
        Set<NodeId> directChildren;
        if (reversed) {
            directChildren = node.dependencies();
        } else {
            directChildren = dependents.getOrDefault(node.id(), Set.of());
        }
        // 推移的簡約: 冗長な子を除外
        return removeTransitiveChildren(directChildren);
    }

    /**
     * 推移的簡約を行い、冗長な子を除外する。
     *
     * <p>例: A の子が {B, D} で、D が B を経由して A に到達できる場合、A → D は冗長。
     */
    private Set<NodeId> removeTransitiveChildren(Set<NodeId> children) {
        if (children.size() <= 1) {
            return children;
        }

        Set<NodeId> result = new HashSet<>(children);

        for (NodeId child : children) {
            // この子が他の子を経由して到達可能か確認
            if (isReachableThroughOtherChildren(child, children)) {
                result.remove(child);
            }
        }

        return result;
    }

    /**
     * 指定された子が、他の子を経由して到達可能かどうかを確認する。
     *
     * @param target 確認対象の子
     * @param allChildren 全ての子の集合
     * @return target が他の子を経由して到達可能な場合 true
     */
    private boolean isReachableThroughOtherChildren(NodeId target, Set<NodeId> allChildren) {
        for (NodeId otherChild : allChildren) {
            if (otherChild.equals(target)) {
                continue;
            }

            // otherChild が target の祖先かどうか確認
            if (isAncestor(otherChild, target, new HashSet<>())) {
                return true;
            }
        }

        return false;
    }

    /** ancestor が descendant の祖先かどうかを確認する（再帰的探索）。 */
    private boolean isAncestor(NodeId ancestor, NodeId descendant, Set<NodeId> visited) {
        if (visited.contains(descendant)) {
            return false;
        }
        visited.add(descendant);

        MigrationNode node = nodeMap.get(descendant);
        if (node == null) {
            return false;
        }

        Set<NodeId> parents = node.dependencies();
        if (parents.contains(ancestor)) {
            return true;
        }

        for (NodeId parent : parents) {
            if (isAncestor(ancestor, parent, visited)) {
                return true;
            }
        }

        return false;
    }

    private int findOrCreateColumn(List<ColumnState> columns) {
        for (int i = 0; i < columns.size(); i++) {
            if (!columns.get(i).isActive()) {
                return i;
            }
        }
        return columns.size();
    }

    private void ensureColumnExists(List<ColumnState> columns, int column) {
        while (columns.size() <= column) {
            columns.add(new ColumnState());
        }
    }

    private String buildNodeLine(List<ColumnState> columns, int nodeCol) {
        StringBuilder sb = new StringBuilder();
        int maxCol = Math.max(columns.size(), nodeCol + 1);

        for (int col = 0; col < maxCol; col++) {
            if (col == nodeCol) {
                sb.append("●");
            } else if (col < columns.size() && columns.get(col).isActive()) {
                sb.append("│");
            } else {
                sb.append(" ");
            }
            if (col < maxCol - 1) {
                sb.append(" ");
            }
        }

        return sb.toString().stripTrailing();
    }

    private String buildBranchLine(
            List<ColumnState> columns, int nodeCol, List<Integer> childCols) {
        StringBuilder sb = new StringBuilder();
        int minCol = Collections.min(childCols);
        int maxCol = Collections.max(childCols);

        for (int col = 0; col <= Math.max(maxCol, columns.size() - 1); col++) {
            if (col == nodeCol) {
                sb.append("├");
            } else if (childCols.contains(col)) {
                if (col == maxCol) {
                    sb.append("┐");
                } else {
                    sb.append("┬");
                }
            } else if (col > minCol && col < maxCol) {
                sb.append("─");
            } else if (col < columns.size() && columns.get(col).isActive()) {
                sb.append("│");
            } else {
                sb.append(" ");
            }

            if (col >= minCol && col < maxCol) {
                sb.append("─");
            } else if (col < Math.max(maxCol, columns.size() - 1)) {
                sb.append(" ");
            }
        }

        return sb.toString().stripTrailing();
    }

    private String buildMergeLine(
            List<ColumnState> columns, List<Integer> parentCols, int targetCol) {
        StringBuilder sb = new StringBuilder();
        int minCol = Collections.min(parentCols);
        int maxCol = Collections.max(parentCols);

        for (int col = 0; col <= Math.max(maxCol, columns.size() - 1); col++) {
            if (col == targetCol) {
                sb.append("├");
            } else if (parentCols.contains(col)) {
                if (col == maxCol) {
                    sb.append("┘");
                } else if (col > minCol) {
                    sb.append("┴");
                } else {
                    sb.append("│");
                }
            } else if (col > minCol && col < maxCol) {
                sb.append("─");
            } else if (col < columns.size() && columns.get(col).isActive()) {
                sb.append("│");
            } else {
                sb.append(" ");
            }

            if (col >= minCol && col < maxCol) {
                sb.append("─");
            } else if (col < Math.max(maxCol, columns.size() - 1)) {
                sb.append(" ");
            }
        }

        return sb.toString().stripTrailing();
    }

    private String buildConnectorLine(List<ColumnState> columns) {
        StringBuilder sb = new StringBuilder();
        for (int col = 0; col < columns.size(); col++) {
            if (columns.get(col).isActive()) {
                sb.append("│");
            } else {
                sb.append(" ");
            }
            if (col < columns.size() - 1) {
                sb.append(" ");
            }
        }
        return sb.toString().stripTrailing();
    }

    private boolean hasActiveColumns(List<ColumnState> columns) {
        for (ColumnState state : columns) {
            if (state.isActive()) {
                return true;
            }
        }
        return false;
    }
}
