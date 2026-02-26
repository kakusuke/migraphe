package io.github.kakusuke.migraphe.core.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GraphCanvas")
class GraphCanvasTest {

    @Test
    @DisplayName("インスタンスを生成できる")
    void shouldInstantiate() {
        GraphCanvas canvas = new GraphCanvas();

        assertThat(canvas).isNotNull();
    }

    @Test
    @DisplayName("layout 後に lineInfos がノード順を返す")
    void layoutProducesNodeLineInfos() {
        MigrationNode nodeA = TestHelpers.node("a").name("A").build();
        MigrationNode nodeB = TestHelpers.node("b").name("B").dependencies(NodeId.of("a")).build();

        DominatorTree dt = new DominatorTree(List.of(nodeA, nodeB), false);
        GraphCanvas canvas = new GraphCanvas();
        canvas.layout(dt);

        List<NodeLineInfo> infos = canvas.getNodeLineInfos();
        assertThat(infos).hasSize(2);
        assertThat(infos.get(0).node()).isEqualTo(nodeA);
        assertThat(infos.get(1).node()).isEqualTo(nodeB);
    }

    @Test
    @DisplayName("layout 後に render が各ノード行を含む文字列リストを返す")
    void renderProducesLinesWithNodes() {
        MigrationNode nodeA = TestHelpers.node("a").name("A").build();
        MigrationNode nodeB = TestHelpers.node("b").name("B").dependencies(NodeId.of("a")).build();

        DominatorTree dt = new DominatorTree(List.of(nodeA, nodeB), false);
        GraphCanvas canvas = new GraphCanvas();
        canvas.layout(dt);

        List<String> lines = canvas.render(n -> n.id().value());
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).contains("●").contains("a");
        assertThat(lines.get(2)).contains("●").contains("b");
    }

    @Test
    @DisplayName("render はコネクタ行を含む全行を返す")
    void renderIncludesConnectorLines() {
        MigrationNode nodeA = TestHelpers.node("a").name("A").build();
        MigrationNode nodeB = TestHelpers.node("b").name("B").dependencies(NodeId.of("a")).build();

        DominatorTree dt = new DominatorTree(List.of(nodeA, nodeB), false);
        GraphCanvas canvas = new GraphCanvas();
        canvas.layout(dt);

        List<String> lines = canvas.render(n -> n.id().value());
        assertThat(lines).hasSize(3);
        assertThat(lines.get(1)).contains("│");
    }
}
