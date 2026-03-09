package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.NodeId;

/** ツリー外エッジ（フォークでもなく親子でもないDAGエッジ）。 */
public record NonTreeEdge(NodeId source, NodeId target) {}
