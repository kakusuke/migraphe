package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;

record GroupInfo(NodeId target, List<NodeId> sources, int startRow, int endRow) {}
