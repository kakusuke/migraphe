package io.github.kakusuke.migraphe.core.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import org.junit.jupiter.api.Test;

class NonDomEdgeTest {

    @Test
    void shouldHoldSourceAndTarget() {
        NodeId src = NodeId.of("a");
        NodeId tgt = NodeId.of("b");

        NonDomEdge edge = new NonDomEdge(src, tgt);

        assertThat(edge.source()).isEqualTo(src);
        assertThat(edge.target()).isEqualTo(tgt);
    }
}
