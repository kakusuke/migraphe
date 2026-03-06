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
    @DisplayName("renderGrid() は a→b の直線チェーンで 3 行を返し、各行に正しい記号を含む")
    void renderGridProducesCorrectLinesForLinearChain() {
        MigrationNode nodeA = TestHelpers.node("a").name("A").build();
        MigrationNode nodeB = TestHelpers.node("b").name("B").dependencies(NodeId.of("a")).build();

        DominatorTree dt = new DominatorTree(List.of(nodeA, nodeB), false);
        GraphCanvas canvas = new GraphCanvas();
        canvas.layout(dt);

        List<String> lines = canvas.renderGrid(n -> n.id().value());

        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).contains("●").contains("a");
        assertThat(lines.get(1)).contains("│");
        assertThat(lines.get(2)).contains("●").contains("b");
    }

    @Test
    @DisplayName("renderGrid() はダイヤモンド構造でマージ行（┘）を挿入し、直後の行に d のノードを配置する")
    void renderGridInsertsMergeRowBeforeDiamondTarget() {
        MigrationNode nodeA = TestHelpers.node("a").build();
        MigrationNode nodeB = TestHelpers.node("b").dependencies(NodeId.of("a")).build();
        MigrationNode nodeC = TestHelpers.node("c").dependencies(NodeId.of("a")).build();
        MigrationNode nodeD =
                TestHelpers.node("d").dependencies(NodeId.of("b"), NodeId.of("c")).build();

        DominatorTree dt = new DominatorTree(List.of(nodeA, nodeB, nodeC, nodeD), false);
        GraphCanvas canvas = new GraphCanvas();
        canvas.layout(dt);

        List<String> lines = canvas.renderGrid(n -> n.id().value());

        int mergeRowIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("┘")) {
                mergeRowIndex = i;
                break;
            }
        }
        assertThat(mergeRowIndex).isGreaterThan(0);
        assertThat(lines.get(mergeRowIndex + 1)).contains("●").contains("d");
    }

    @Test
    @DisplayName("branch ノードは fork 記号（├）の直右にノード記号（●）を配置する（├● であり ├─● でない）")
    void branchNodeShouldPlaceTaskCellDirectlyAfterForkSymbol() {
        MigrationNode nodeA = TestHelpers.node("a").build();
        MigrationNode nodeB = TestHelpers.node("b").dependencies(NodeId.of("a")).build();
        MigrationNode nodeC = TestHelpers.node("c").dependencies(NodeId.of("a")).build();

        DominatorTree dt = new DominatorTree(List.of(nodeA, nodeB, nodeC), false);
        GraphCanvas canvas = new GraphCanvas();
        canvas.layout(dt);

        List<String> lines = canvas.render(n -> n.id().value());
        assertThat(lines).anyMatch(line -> line.contains("├●"));
        assertThat(lines).noneMatch(line -> line.contains("├─●"));
    }

    @Test
    @DisplayName("branch ノード行では ● から最初のレーン角（┐/┤）まで水平線が途切れない")
    void branchNodeRowShouldHaveNoGapsBetweenNodeSymbolAndLaneCorner() {
        MigrationNode nodeA = TestHelpers.node("a").build();
        MigrationNode nodeB = TestHelpers.node("b").dependencies(NodeId.of("a")).build();
        MigrationNode nodeC = TestHelpers.node("c").dependencies(NodeId.of("a")).build();
        MigrationNode nodeE = TestHelpers.node("e").dependencies(NodeId.of("a")).build();
        MigrationNode nodeD =
                TestHelpers.node("d")
                        .dependencies(NodeId.of("b"), NodeId.of("c"), NodeId.of("e"))
                        .build();

        DominatorTree dt = new DominatorTree(List.of(nodeA, nodeB, nodeC, nodeE, nodeD), false);
        GraphCanvas canvas = new GraphCanvas();
        canvas.layout(dt);

        List<String> lines = canvas.render(n -> n.id().value());
        assertThat(lines)
                .filteredOn(line -> line.contains("├●"))
                .noneMatch(
                        line ->
                                line.replaceFirst(".*●", "")
                                        .replaceFirst("[┐┤].*", "")
                                        .contains("│"));
    }

    @Test
    @DisplayName("複数レーンがある場合、分岐ノード行の ● とレーン角（┐/┤）の間に空白セルが表示されない")
    void branchNodeRowShouldNotHaveSpacesBetweenNodeAndLaneCornerWhenMultipleLanes() {
        // G0 (lane 0, endRow大): sources={n(row2), m(row5)}, target=q0(row7)
        // G1 (lane 1, endRow小): sources={p(row1), m(row5)}, target=q1(row6)
        // p の行 (row 1) では lane0 (G0.startRow=2) が未開始 → SpaceCell → ' ┐' の空白バグ
        MigrationNode nodeA = TestHelpers.node("a").build();
        MigrationNode nodeP = TestHelpers.node("p").dependencies(NodeId.of("a")).build();
        MigrationNode nodeN = TestHelpers.node("n").dependencies(NodeId.of("a")).build();
        MigrationNode nodeB = TestHelpers.node("b").dependencies(NodeId.of("a")).build();
        MigrationNode nodeM = TestHelpers.node("m").dependencies(NodeId.of("b")).build();
        MigrationNode nodeQ1 =
                TestHelpers.node("q1").dependencies(NodeId.of("p"), NodeId.of("m")).build();
        MigrationNode nodeQ0 =
                TestHelpers.node("q0").dependencies(NodeId.of("m"), NodeId.of("n")).build();

        DominatorTree dt =
                new DominatorTree(
                        List.of(nodeA, nodeP, nodeN, nodeB, nodeM, nodeQ1, nodeQ0), false);
        GraphCanvas canvas = new GraphCanvas();
        canvas.layout(dt);

        List<String> lines = canvas.render(n -> n.id().value());
        // ┐/┤ を含む分岐ノード行のみを対象に、● からレーン角までの間に空白がない
        assertThat(lines)
                .filteredOn(
                        line -> line.contains("├●") && (line.contains("┐") || line.contains("┤")))
                .noneMatch(
                        line ->
                                line.replaceFirst(".*●", "")
                                        .replaceFirst("[┐┤].*", "")
                                        .contains(" "));
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
