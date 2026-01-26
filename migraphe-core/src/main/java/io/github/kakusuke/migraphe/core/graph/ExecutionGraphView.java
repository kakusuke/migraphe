package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.*;

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
        List<NodeId> columns = new ArrayList<>();

        for (int i = 0; i < sortedNodes.size(); i++) {
            MigrationNode node = sortedNodes.get(i);
            boolean isLast = (i == sortedNodes.size() - 1);

            Set<NodeId> parents = getParents(node);
            Set<NodeId> children = getChildren(node);

            List<Integer> parentCols = new ArrayList<>();
            for (NodeId parentId : parents) {
                int col = columns.indexOf(parentId);
                if (col >= 0) {
                    parentCols.add(col);
                }
            }
            Collections.sort(parentCols);

            boolean hasChildren = !children.isEmpty();

            int nodeCol;
            if (!parentCols.isEmpty()) {
                nodeCol = parentCols.get(0);
            } else {
                nodeCol = findOrCreateColumn(columns);
            }

            String branchLine = null;
            String mergeLine = null;

            if (parentCols.size() > 1) {
                mergeLine = buildMergeLine(columns, parentCols, nodeCol);
                for (int col : parentCols) {
                    if (col != nodeCol && col < columns.size()) {
                        columns.set(col, null);
                    }
                }
            } else if (parentCols.size() == 1) {
                int parentCol = parentCols.get(0);
                if (parentCol < columns.size()) {
                    columns.set(parentCol, null);
                }
            }

            String graphPrefix = buildNodeLine(columns, nodeCol);

            if (hasChildren) {
                ensureColumnExists(columns, nodeCol);
                columns.set(nodeCol, node.id());
            }

            if (children.size() > 1) {
                List<Integer> childCols = new ArrayList<>();
                childCols.add(nodeCol);
                for (int c = 1; c < children.size(); c++) {
                    int newCol = findOrCreateColumn(columns);
                    ensureColumnExists(columns, newCol);
                    columns.set(newCol, node.id());
                    childCols.add(newCol);
                }
                branchLine = buildBranchLine(columns, nodeCol, childCols);
            }

            String connectorLine = null;
            if (!isLast && hasActiveColumns(columns)) {
                connectorLine = buildConnectorLine(columns);
            }

            result.add(new NodeLineInfo(node, graphPrefix, mergeLine, branchLine, connectorLine));
        }

        return result;
    }

    private Set<NodeId> getParents(MigrationNode node) {
        if (reversed) {
            return dependents.getOrDefault(node.id(), Set.of());
        } else {
            return node.dependencies();
        }
    }

    private Set<NodeId> getChildren(MigrationNode node) {
        if (reversed) {
            return node.dependencies();
        } else {
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
}
