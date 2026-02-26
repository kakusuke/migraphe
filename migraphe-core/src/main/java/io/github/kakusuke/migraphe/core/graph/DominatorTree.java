package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.*;
import org.jspecify.annotations.Nullable;

final class DominatorTree {

    private static final NodeId VIRTUAL_ROOT = NodeId.of("__virtual_root__");

    final Map<NodeId, MigrationNode> nodeMap;
    final Map<NodeId, List<NodeId>> childrenOf;
    final Map<NodeId, List<NodeId>> parentsOf;
    final Map<NodeId, NodeId> idom;
    final Map<NodeId, List<NodeId>> domChildren;
    final Map<NodeId, @Nullable NodeId> trunkChild;
    final List<NodeId> roots;
    final boolean hasVirtualRoot;

    DominatorTree(List<MigrationNode> sortedNodes, boolean reversed) {
        this.nodeMap = new LinkedHashMap<>();
        for (MigrationNode node : sortedNodes) {
            nodeMap.put(node.id(), node);
        }

        Map<NodeId, Set<NodeId>> dependents = new LinkedHashMap<>();
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

        // Step 1: 支配木の構築
        List<NodeId> topoOrder = sortedNodes.stream().map(MigrationNode::id).toList();
        List<NodeId> rootsList = new ArrayList<>();
        for (NodeId id : topoOrder) {
            if (parentsOf(id).isEmpty()) {
                rootsList.add(id);
            }
        }
        this.roots = List.copyOf(rootsList);

        // idom の計算
        Map<NodeId, NodeId> idomMap = new LinkedHashMap<>();
        boolean virtualRoot = roots.size() > 1;
        this.hasVirtualRoot = virtualRoot;

        if (virtualRoot) {
            for (NodeId root : roots) {
                idomMap.put(root, VIRTUAL_ROOT);
            }
        }

        for (NodeId nodeId : topoOrder) {
            if (roots.contains(nodeId)) continue;
            List<NodeId> p = parentsOf(nodeId);
            if (p.size() == 1) {
                idomMap.put(nodeId, p.get(0));
            } else {
                // 複数親: LCA を計算
                NodeId lca = p.get(0);
                for (int i = 1; i < p.size(); i++) {
                    lca = lcaInDomTree(lca, p.get(i), idomMap);
                }
                idomMap.put(nodeId, lca);
            }
        }
        this.idom = Map.copyOf(idomMap);

        // 支配木の子リストを構築
        Map<NodeId, List<NodeId>> domChildrenMap = new LinkedHashMap<>();
        for (Map.Entry<NodeId, NodeId> entry : idomMap.entrySet()) {
            domChildrenMap
                    .computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(entry.getKey());
        }
        this.domChildren = Map.copyOf(domChildrenMap);

        // Step 2: Trunk 選択（ノードがない場合はスキップ）
        Map<NodeId, Integer> subtreeDepth = new LinkedHashMap<>();
        Map<NodeId, @Nullable NodeId> trunkChildMap = new LinkedHashMap<>();
        if (!roots.isEmpty()) {
            computeSubtreeDepth(
                    hasVirtualRoot ? VIRTUAL_ROOT : roots.get(0), domChildrenMap, subtreeDepth);
            computeTrunkChild(
                    hasVirtualRoot ? VIRTUAL_ROOT : roots.get(0),
                    domChildrenMap,
                    subtreeDepth,
                    trunkChildMap,
                    topoOrder);
        }
        this.trunkChild = Collections.unmodifiableMap(trunkChildMap);
    }

    // ========== 支配木ユーティリティ ==========

    NodeId lcaInDomTree(NodeId a, NodeId b, Map<NodeId, NodeId> idomMap) {
        Set<NodeId> ancestorsOfA = new LinkedHashSet<>();
        NodeId current = a;
        ancestorsOfA.add(current);
        while (idomMap.containsKey(current)) {
            current = idomMap.get(current);
            ancestorsOfA.add(current);
        }

        current = b;
        while (!ancestorsOfA.contains(current)) {
            if (!idomMap.containsKey(current)) break;
            current = idomMap.get(current);
        }
        return current;
    }

    int computeSubtreeDepth(
            NodeId nodeId,
            Map<NodeId, List<NodeId>> domChildrenMap,
            Map<NodeId, Integer> subtreeDepth) {
        List<NodeId> children = domChildrenMap.getOrDefault(nodeId, List.of());
        if (children.isEmpty()) {
            subtreeDepth.put(nodeId, 0);
            return 0;
        }
        int maxDepth = 0;
        for (NodeId child : children) {
            int d = computeSubtreeDepth(child, domChildrenMap, subtreeDepth);
            maxDepth = Math.max(maxDepth, d);
        }
        int depth = maxDepth + 1;
        subtreeDepth.put(nodeId, depth);
        return depth;
    }

    void computeTrunkChild(
            NodeId nodeId,
            Map<NodeId, List<NodeId>> domChildrenMap,
            Map<NodeId, Integer> subtreeDepth,
            Map<NodeId, @Nullable NodeId> trunkChildMap,
            List<NodeId> topoOrder) {
        List<NodeId> children = domChildrenMap.getOrDefault(nodeId, List.of());
        if (children.isEmpty()) {
            trunkChildMap.put(nodeId, null);
        } else if (children.size() == 1) {
            trunkChildMap.put(nodeId, children.get(0));
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
            trunkChildMap.put(nodeId, best);
        }

        for (NodeId child : children) {
            computeTrunkChild(child, domChildrenMap, subtreeDepth, trunkChildMap, topoOrder);
        }
    }

    // ========== 非支配木辺 ==========

    List<NonDomEdge> findNonDomEdges() {
        List<NonDomEdge> edges = new ArrayList<>();
        for (Map.Entry<NodeId, List<NodeId>> entry : this.childrenOf.entrySet()) {
            NodeId parent = entry.getKey();
            for (NodeId child : entry.getValue()) {
                NodeId childIdom = this.idom.get(child);
                if (childIdom != null && !childIdom.equals(parent)) {
                    edges.add(new NonDomEdge(parent, child));
                }
            }
        }
        return edges;
    }

    // ========== DAG ユーティリティ ==========

    List<NodeId> parentsOf(NodeId nodeId) {
        return this.parentsOf.getOrDefault(nodeId, List.of());
    }

    List<NodeId> childrenOf(NodeId nodeId) {
        return this.childrenOf.getOrDefault(nodeId, List.of());
    }

    boolean hasVisibleStructure(NodeId nodeId) {
        return !childrenOf(nodeId).isEmpty();
    }

    @Nullable NodeId subgraphRoot(NodeId nodeId) {
        List<NodeId> p = parentsOf(nodeId);
        if (p.isEmpty()) return nodeId;
        for (NodeId parentId : p) {
            if (this.nodeMap.containsKey(parentId)) {
                return subgraphRoot(parentId);
            }
        }
        return nodeId;
    }

    Set<NodeId> removeTransitive(Set<NodeId> ids) {
        if (ids.size() <= 1) return ids;
        Set<NodeId> result = new HashSet<>(ids);
        for (NodeId id : ids) {
            if (isReachableViaOthers(id, ids)) {
                result.remove(id);
            }
        }
        return result;
    }

    Set<NodeId> removeTransitiveParents(Set<NodeId> parentIds) {
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

    boolean canReachDown(NodeId from, NodeId target, Set<NodeId> visited) {
        if (from.equals(target)) return true;
        if (!visited.add(from)) return false;
        for (NodeId child : this.childrenOf.getOrDefault(from, List.of())) {
            if (canReachDown(child, target, visited)) return true;
        }
        return false;
    }

    boolean isReachableViaOthers(NodeId target, Set<NodeId> all) {
        for (NodeId other : all) {
            if (other.equals(target)) continue;
            if (isAncestor(other, target, new HashSet<>())) return true;
        }
        return false;
    }

    boolean isAncestor(NodeId ancestor, NodeId descendant, Set<NodeId> visited) {
        if (visited.contains(descendant)) return false;
        visited.add(descendant);
        MigrationNode node = this.nodeMap.get(descendant);
        if (node == null) return false;
        Set<NodeId> p = node.dependencies();
        if (p.contains(ancestor)) return true;
        for (NodeId pid : p) {
            if (isAncestor(ancestor, pid, visited)) return true;
        }
        return false;
    }
}
