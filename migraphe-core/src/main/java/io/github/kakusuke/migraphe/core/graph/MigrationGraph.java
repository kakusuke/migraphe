package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.core.common.ValidationResult;
import java.util.*;

/** マイグレーションノードの有向非巡回グラフ（DAG）。 集約ルート - グラフの整合性を保証する。 */
public final class MigrationGraph implements MigrationGraphView {
    private final Map<NodeId, MigrationNode> nodes;
    private final Map<NodeId, Set<NodeId>> adjacencyList; // ノード -> 依存先ノード

    private MigrationGraph() {
        this.nodes = new HashMap<>();
        this.adjacencyList = new HashMap<>();
    }

    /** ノードをグラフに追加する。依存関係の存在は validate() で検査する。 */
    public void addNode(MigrationNode node) {
        if (nodes.containsKey(node.id())) {
            throw new IllegalArgumentException("Node already exists: " + node.id());
        }

        nodes.put(node.id(), node);
        adjacencyList.put(node.id(), new HashSet<>(node.dependencies()));
    }

    /** 依存関係のないルートノード（最初に実行できるノード）を取得 */
    @Override
    public Set<MigrationNode> getRoots() {
        return nodes.values().stream()
                .filter(node -> adjacencyList.getOrDefault(node.id(), Set.of()).isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 指定されたノードの直接の依存先を取得 */
    @Override
    public Set<NodeId> getDependencies(NodeId nodeId) {
        return Set.copyOf(adjacencyList.getOrDefault(nodeId, Set.of()));
    }

    /** 指定されたノードに依存しているノード（依存元）を取得 */
    @Override
    public Set<NodeId> getDependents(NodeId nodeId) {
        return adjacencyList.entrySet().stream()
                .filter(entry -> entry.getValue().contains(nodeId))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 指定されたノードに直接/間接的に依存する全ノードを取得（再帰） */
    public Set<NodeId> getAllDependents(NodeId nodeId) {
        Set<NodeId> result = new HashSet<>();
        collectDependents(nodeId, result);
        return result;
    }

    private void collectDependents(NodeId nodeId, Set<NodeId> collected) {
        for (NodeId dependent : getDependents(nodeId)) {
            if (collected.add(dependent)) {
                collectDependents(dependent, collected);
            }
        }
    }

    /** 指定されたノードが直接/間接的に依存する全ノードを取得（再帰） */
    public Set<NodeId> getAllDependencies(NodeId nodeId) {
        Set<NodeId> result = new HashSet<>();
        collectDependencies(nodeId, result);
        return result;
    }

    private void collectDependencies(NodeId nodeId, Set<NodeId> collected) {
        for (NodeId dependency : getDependencies(nodeId)) {
            if (collected.add(dependency)) {
                collectDependencies(dependency, collected);
            }
        }
    }

    /** ノードをIDで取得 */
    @Override
    public Optional<MigrationNode> getNode(NodeId nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    /** グラフが循環参照を持っているか検証（DFSベース） */
    public boolean hasCycle() {
        Set<NodeId> visited = new HashSet<>();
        Set<NodeId> recursionStack = new HashSet<>();

        for (NodeId nodeId : nodes.keySet()) {
            if (hasCycleUtil(nodeId, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCycleUtil(NodeId nodeId, Set<NodeId> visited, Set<NodeId> recursionStack) {
        if (recursionStack.contains(nodeId)) {
            return true; // サイクル検出
        }

        if (visited.contains(nodeId)) {
            return false;
        }

        visited.add(nodeId);
        recursionStack.add(nodeId);

        for (NodeId dependency : getDependencies(nodeId)) {
            if (hasCycleUtil(dependency, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(nodeId);
        return false;
    }

    /** グラフのバリデーション */
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();

        // サイクルチェック
        if (hasCycle()) {
            errors.add("Graph contains a cycle (circular dependency)");
        }

        // 全ての依存先ノードが存在するかチェック (node.dependencies() を直接参照: adjacency は
        // fromNodesUp/Down でリスト内に絞り込まれている場合があるため、ソース・オブ・トゥルースを使う)
        for (MigrationNode node : nodes.values()) {
            for (NodeId depId : node.dependencies()) {
                if (!nodes.containsKey(depId)) {
                    errors.add("Node " + node.id() + " depends on non-existent node: " + depId);
                }
            }
        }

        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }

    /** グラフ内のノード数 */
    @Override
    public int size() {
        return nodes.size();
    }

    /** 全ノードを取得 */
    @Override
    public Collection<MigrationNode> allNodes() {
        return List.copyOf(nodes.values());
    }

    public static MigrationGraph create() {
        return new MigrationGraph();
    }

    public static MigrationGraph fromNodesUp(List<MigrationNode> nodes) {
        MigrationGraph graph = new MigrationGraph();
        Set<NodeId> nodeIds = new HashSet<>();
        for (MigrationNode node : nodes) {
            nodeIds.add(node.id());
        }
        for (MigrationNode node : nodes) {
            graph.nodes.put(node.id(), node);
            Set<NodeId> filteredDeps = new HashSet<>();
            for (NodeId depId : node.dependencies()) {
                if (nodeIds.contains(depId)) {
                    filteredDeps.add(depId);
                }
            }
            graph.adjacencyList.put(node.id(), filteredDeps);
        }
        return graph;
    }

    public static MigrationGraph fromNodesDown(List<MigrationNode> nodes) {
        MigrationGraph graph = new MigrationGraph();
        for (MigrationNode node : nodes) {
            graph.nodes.put(node.id(), node);
            graph.adjacencyList.put(node.id(), new HashSet<>());
        }
        for (MigrationNode node : nodes) {
            for (NodeId parentId : node.dependencies()) {
                Set<NodeId> parentAdjacency = graph.adjacencyList.get(parentId);
                if (parentAdjacency != null) {
                    parentAdjacency.add(node.id());
                }
            }
        }
        return graph;
    }
}
