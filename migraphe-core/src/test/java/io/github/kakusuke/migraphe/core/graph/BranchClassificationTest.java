package io.github.kakusuke.migraphe.core.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import org.junit.jupiter.api.Test;

class BranchClassificationTest {

    @Test
    void shouldHoldPreTrunkAndPostTrunk() {
        List<NodeId> pre = List.of(NodeId.of("a"));
        List<NodeId> post = List.of(NodeId.of("b"), NodeId.of("c"));

        BranchClassification bc = new BranchClassification(pre, post);

        assertThat(bc.preTrunk()).isEqualTo(pre);
        assertThat(bc.postTrunk()).isEqualTo(post);
    }
}
