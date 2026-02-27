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

    @Test
    @DisplayName("マージ行（┘）の左レーン文字が、ターゲット行から始まるレーンを ┼ と誤表示しない")
    void mergeRowShouldNotShowSpuriousCrossCharactersBeforeClosing() {
        // d はノード b,c の非支配木辺ターゲット（マージ行が挿入される）
        // かつ d はノード e への非支配木辺のソース（d の行からレーンが開始する）
        // → d 直前のマージ行の時点ではまだ開始していないレーンが ┼ と誤表示されないことを検証する
        MigrationNode nodeA = TestHelpers.node("a").build();
        MigrationNode nodeB = TestHelpers.node("b").dependencies(NodeId.of("a")).build();
        MigrationNode nodeC = TestHelpers.node("c").dependencies(NodeId.of("a")).build();
        MigrationNode nodeD =
                TestHelpers.node("d").dependencies(NodeId.of("b"), NodeId.of("c")).build();
        MigrationNode nodeF =
                TestHelpers.node("f").dependencies(NodeId.of("b"), NodeId.of("c")).build();
        MigrationNode nodeX = TestHelpers.node("x").dependencies(NodeId.of("a")).build();
        MigrationNode nodeE =
                TestHelpers.node("e").dependencies(NodeId.of("d"), NodeId.of("x")).build();

        DominatorTree dt =
                new DominatorTree(List.of(nodeA, nodeB, nodeC, nodeD, nodeF, nodeX, nodeE), false);
        GraphCanvas canvas = new GraphCanvas();
        canvas.layout(dt);

        List<String> lines = canvas.render(n -> n.id().value());
        List<String> mergeLines = lines.stream().filter(line -> line.contains("┘")).toList();
        assertThat(mergeLines).isNotEmpty();
        // マージ行でターゲット行から始まるレーンが ┼ と誤表示されると隣接 ┼┼ が現れる
        assertThat(mergeLines).noneMatch(line -> line.contains("┼┼"));
    }

    @Test
    @DisplayName("マージ行の閉じ括弧（┘）の右側に余計な縦棒（│）が表示されない")
    void mergeRowShouldNotShowExtraVerticalBarsAfterClosing() {
        MigrationNode nodeA = TestHelpers.node("a").build();
        MigrationNode nodeB = TestHelpers.node("b").dependencies(NodeId.of("a")).build();
        MigrationNode nodeC = TestHelpers.node("c").dependencies(NodeId.of("a")).build();
        MigrationNode nodeD =
                TestHelpers.node("d").dependencies(NodeId.of("b"), NodeId.of("c")).build();
        MigrationNode nodeE =
                TestHelpers.node("e").dependencies(NodeId.of("b"), NodeId.of("c")).build();

        DominatorTree dt = new DominatorTree(List.of(nodeA, nodeB, nodeC, nodeD, nodeE), false);
        GraphCanvas canvas = new GraphCanvas();
        canvas.layout(dt);

        List<String> lines = canvas.render(n -> n.id().value());
        assertThat(lines).noneMatch(line -> line.contains("┘│"));
    }
}
