package io.github.kakusuke.migraphe.core.graph;

import static io.github.kakusuke.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import org.junit.jupiter.api.Test;

class MigrationGraphViewTest {

    @Test
    void shouldExposeReadOnlyMethodsThroughMigrationGraphViewInterface() {
        // given
        NodeId id1 = NodeId.of("node-1");
        NodeId id2 = NodeId.of("node-2");
        NodeId id3 = NodeId.of("node-3");

        MigrationNode root1 = node("node-1").build();
        MigrationNode root2 = node("node-2").build();
        MigrationNode dependent = node("node-3").dependencies(id1, id2).build();

        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(root1);
        graph.addNode(root2);
        graph.addNode(dependent);

        // when
        MigrationGraphView view = graph;

        // then
        assertThat(view.size()).isEqualTo(3);
        assertThat(view.allNodes()).containsExactlyInAnyOrder(root1, root2, dependent);
        assertThat(view.getRoots()).containsExactlyInAnyOrder(root1, root2);
        assertThat(view.getNode(id1)).hasValue(root1);
        assertThat(view.getNode(NodeId.of("non-existent"))).isEmpty();
        assertThat(view.getDependencies(id3)).containsExactlyInAnyOrder(id1, id2);
        assertThat(view.getDependencies(id1)).isEmpty();
        assertThat(view.getDependents(id1)).containsExactly(id3);
    }
}
