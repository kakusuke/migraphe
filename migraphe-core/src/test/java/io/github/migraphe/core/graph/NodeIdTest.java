package io.github.migraphe.core.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.migraphe.api.graph.NodeId;
import org.junit.jupiter.api.Test;

class NodeIdTest {

    @Test
    void shouldCreateNodeIdWithValidValue() {
        // given
        String value = "node-123";

        // when
        NodeId nodeId = NodeId.of(value);

        // then
        assertThat(nodeId.value()).isEqualTo(value);
    }

    @Test
    void shouldGenerateUniqueNodeId() {
        // when
        NodeId nodeId1 = NodeId.generate();
        NodeId nodeId2 = NodeId.generate();

        // then
        assertThat(nodeId1.value()).isNotBlank();
        assertThat(nodeId2.value()).isNotBlank();
        assertThat(nodeId1.value()).isNotEqualTo(nodeId2.value());
    }

    @Test
    void shouldThrowExceptionWhenValueIsNull() {
        // when & then
        assertThatThrownBy(() -> NodeId.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("value must not be null");
    }

    @Test
    void shouldThrowExceptionWhenValueIsBlank() {
        // when & then
        assertThatThrownBy(() -> NodeId.of(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NodeId value must not be blank");

        assertThatThrownBy(() -> NodeId.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NodeId value must not be blank");
    }

    @Test
    void shouldBeEqualWhenValuesAreSame() {
        // given
        NodeId nodeId1 = NodeId.of("node-123");
        NodeId nodeId2 = NodeId.of("node-123");

        // when & then
        assertThat(nodeId1).isEqualTo(nodeId2);
        assertThat(nodeId1.hashCode()).isEqualTo(nodeId2.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenValuesAreDifferent() {
        // given
        NodeId nodeId1 = NodeId.of("node-123");
        NodeId nodeId2 = NodeId.of("node-456");

        // when & then
        assertThat(nodeId1).isNotEqualTo(nodeId2);
    }
}
