package io.github.kakusuke.migraphe.core.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroupInfoTest {

    @Test
    void shouldHoldFields() {
        NodeId target = NodeId.of("t");
        List<NodeId> sources = List.of(NodeId.of("a"), NodeId.of("b"));

        GroupInfo gi = new GroupInfo(target, sources, 3, 7);

        assertThat(gi.target()).isEqualTo(target);
        assertThat(gi.sources()).isEqualTo(sources);
        assertThat(gi.startRow()).isEqualTo(3);
        assertThat(gi.endRow()).isEqualTo(7);
    }
}
