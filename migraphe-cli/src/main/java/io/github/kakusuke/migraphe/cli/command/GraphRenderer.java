package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.*;
import org.jspecify.annotations.Nullable;

/**
 * DAGをASCIIグラフとして描画するユーティリティ。
 *
 * <p>git log --graph 風の表示を実現する。
 */
final class GraphRenderer {

    private final List<MigrationNode> sortedNodes;
    private final Map<NodeId, MigrationNode> nodeMap;
    private final Map<NodeId, Set<NodeId>> dependents; // ノード -> そのノードに依存しているノード
    private final boolean reversed;

    GraphRenderer(List<MigrationNode> sortedNodes) {
        this(sortedNodes, false);
    }

    /**
     * コンストラクタ。
     *
     * @param sortedNodes ソート済みノードリスト
     * @param reversed true の場合、逆順モード（DOWN用）。依存関係を逆に解釈する。
     */
    GraphRenderer(List<MigrationNode> sortedNodes, boolean reversed) {
        this.sortedNodes = List.copyOf(sortedNodes);
        this.nodeMap = new HashMap<>();
        this.dependents = new HashMap<>();
        this.reversed = reversed;

        // ノードマップを構築
        for (MigrationNode node : sortedNodes) {
            nodeMap.put(node.id(), node);
        }

        // 依存元（dependents）マップを構築
        for (MigrationNode node : sortedNodes) {
            for (NodeId dep : node.dependencies()) {
                dependents.computeIfAbsent(dep, k -> new HashSet<>()).add(node.id());
            }
        }
    }

    /**
     * 各ノードに対してグラフ行を生成する。
     *
     * @return ノードごとのグラフ表示情報のリスト
     */
    List<NodeGraphInfo> render() {
        List<NodeGraphInfo> result = new ArrayList<>();

        // アクティブな列を追跡（どのノードIDがどの列を使っているか）
        // null = 空き列
        List<NodeId> columns = new ArrayList<>();

        for (int i = 0; i < sortedNodes.size(); i++) {
            MigrationNode node = sortedNodes.get(i);
            boolean isLast = (i == sortedNodes.size() - 1);

            // 逆順モードでは親子関係を逆に解釈
            Set<NodeId> parents = getParents(node);
            Set<NodeId> children = getChildren(node);

            // このノードの親がどの列にあるか確認
            List<Integer> parentCols = new ArrayList<>();
            for (NodeId parentId : parents) {
                int col = columns.indexOf(parentId);
                if (col >= 0) {
                    parentCols.add(col);
                }
            }
            Collections.sort(parentCols);

            boolean hasChildren = !children.isEmpty();

            // このノードを配置する列を決定
            int nodeCol;
            if (!parentCols.isEmpty()) {
                // 親のうち最小の列を使用
                nodeCol = parentCols.get(0);
            } else {
                // 親がない場合（ルートノード）は空いている列を探す
                nodeCol = findOrCreateColumn(columns);
            }

            // 分岐行を生成（複数の子がある場合、次のノードを処理する前に分岐を表示）
            // これは前のノードの処理で生成されるべき
            String branchLine = null;

            // マージ行を生成（複数の親がある場合）
            String mergeLine = null;
            if (parentCols.size() > 1) {
                mergeLine = buildMergeLine(columns, parentCols, nodeCol);
                // マージした列を解放
                for (int col : parentCols) {
                    if (col != nodeCol && col < columns.size()) {
                        columns.set(col, null);
                    }
                }
            } else if (parentCols.size() == 1) {
                // 単一の親の場合、その列を解放してこのノードの列に
                int parentCol = parentCols.get(0);
                if (parentCol < columns.size()) {
                    columns.set(parentCol, null);
                }
            }

            // ノード行を生成
            String nodeLine = buildNodeLine(columns, nodeCol);

            // このノードの列を設定（子がある場合）
            if (hasChildren) {
                ensureColumnExists(columns, nodeCol);
                columns.set(nodeCol, node.id());
            }

            // 分岐行を生成（複数の子がある場合）
            // 子の数を数えて、必要に応じて列を追加
            if (children.size() > 1) {
                // 追加の列を確保
                List<Integer> childCols = new ArrayList<>();
                childCols.add(nodeCol);
                for (int c = 1; c < children.size(); c++) {
                    int newCol = findOrCreateColumn(columns);
                    ensureColumnExists(columns, newCol);
                    columns.set(newCol, node.id()); // 一時的に同じノードIDで埋める
                    childCols.add(newCol);
                }
                branchLine = buildBranchLine(columns, nodeCol, childCols);
            }

            // 接続線を生成（次のノードへの線）
            String connectorLine = null;
            if (!isLast && hasActiveColumns(columns)) {
                connectorLine = buildConnectorLine(columns);
            }

            result.add(new NodeGraphInfo(node, nodeLine, branchLine, mergeLine, connectorLine));
        }

        return result;
    }

    /** 逆順モードに応じて親ノードを取得する。 */
    private Set<NodeId> getParents(MigrationNode node) {
        if (reversed) {
            // 逆順: dependents が親
            return dependents.getOrDefault(node.id(), Set.of());
        } else {
            // 正順: dependencies が親
            return node.dependencies();
        }
    }

    /** 逆順モードに応じて子ノードを取得する。 */
    private Set<NodeId> getChildren(MigrationNode node) {
        if (reversed) {
            // 逆順: dependencies が子
            return node.dependencies();
        } else {
            // 正順: dependents が子
            return dependents.getOrDefault(node.id(), Set.of());
        }
    }

    private int findOrCreateColumn(List<NodeId> columns) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i) == null) {
                return i;
            }
        }
        return columns.size();
    }

    private void ensureColumnExists(List<NodeId> columns, int column) {
        while (columns.size() <= column) {
            columns.add(null);
        }
    }

    private String buildNodeLine(List<NodeId> columns, int nodeCol) {
        StringBuilder sb = new StringBuilder();
        int maxCol = Math.max(columns.size(), nodeCol + 1);

        for (int col = 0; col < maxCol; col++) {
            if (col == nodeCol) {
                sb.append("●");
            } else if (col < columns.size() && columns.get(col) != null) {
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

    private String buildBranchLine(List<NodeId> columns, int nodeCol, List<Integer> childCols) {
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
            } else if (col < columns.size() && columns.get(col) != null) {
                sb.append("│");
            } else {
                sb.append(" ");
            }

            // 列間の接続
            if (col >= minCol && col < maxCol) {
                sb.append("─");
            } else if (col < Math.max(maxCol, columns.size() - 1)) {
                sb.append(" ");
            }
        }

        return sb.toString().stripTrailing();
    }

    private String buildMergeLine(List<NodeId> columns, List<Integer> parentCols, int targetCol) {
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
            } else if (col < columns.size() && columns.get(col) != null) {
                sb.append("│");
            } else {
                sb.append(" ");
            }

            // 列間の接続
            if (col >= minCol && col < maxCol) {
                sb.append("─");
            } else if (col < Math.max(maxCol, columns.size() - 1)) {
                sb.append(" ");
            }
        }

        return sb.toString().stripTrailing();
    }

    private String buildConnectorLine(List<NodeId> columns) {
        StringBuilder sb = new StringBuilder();
        for (int col = 0; col < columns.size(); col++) {
            if (columns.get(col) != null) {
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

    private boolean hasActiveColumns(List<NodeId> columns) {
        for (NodeId id : columns) {
            if (id != null) {
                return true;
            }
        }
        return false;
    }

    /** ノードのグラフ表示情報。 */
    record NodeGraphInfo(
            MigrationNode node,
            String nodeLine,
            @Nullable String branchLine, // 分岐がある場合のみ非null
            @Nullable String mergeLine, // マージがある場合のみ非null
            @Nullable String connectorLine // 次のノードへの接続線、ある場合のみ非null
            ) {}
}
