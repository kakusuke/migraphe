package io.github.kakusuke.migraphe.core.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cell")
class CellTest {

    @Test
    @DisplayName("SpaceCell#symbol() はスペース1文字を返す")
    void spaceCellSymbolReturnsSingleSpace() {
        Cell.SpaceCell spaceCell = new Cell.SpaceCell();

        assertThat(spaceCell.symbol()).isEqualTo(" ");
    }

    @Test
    @DisplayName("TaskCell#symbol() は ● を返す")
    void taskCellSymbolReturnsBullet() {
        Cell.TaskCell taskCell = new Cell.TaskCell(NodeId.of("x"));

        assertThat(taskCell.symbol()).isEqualTo("●");
    }

    @Test
    @DisplayName("VBarCell#symbol() は │ を返す")
    void vBarCellSymbolReturnsVerticalBar() {
        Cell.VBarCell vBarCell = new Cell.VBarCell();

        assertThat(vBarCell.symbol()).isEqualTo("│");
    }

    @Test
    @DisplayName("HBarCell#symbol() は ─ を返す")
    void hBarCellSymbolReturnsHorizontalBar() {
        Cell.HBarCell hBarCell = new Cell.HBarCell();

        assertThat(hBarCell.symbol()).isEqualTo("─");
    }

    @Test
    @DisplayName("ForkCell#symbol() は ├ を返す")
    void forkCellSymbolReturnsFork() {
        Cell.ForkCell forkCell = new Cell.ForkCell();

        assertThat(forkCell.symbol()).isEqualTo("├");
    }

    @Test
    @DisplayName("MergeJoinCell#symbol() は ├ を返す")
    void mergeJoinCellSymbolReturnsFork() {
        Cell.MergeJoinCell mergeJoinCell = new Cell.MergeJoinCell();

        assertThat(mergeJoinCell.symbol()).isEqualTo("├");
    }

    @Test
    @DisplayName("LaneSpaceCell#symbol() はスペース1文字を返す")
    void laneSpaceCellSymbolReturnsSingleSpace() {
        Cell.LaneSpaceCell laneSpaceCell = new Cell.LaneSpaceCell();

        assertThat(laneSpaceCell.symbol()).isEqualTo(" ");
    }

    @Test
    @DisplayName("LanePassCell#symbol() は │ を返す")
    void lanePassCellSymbolReturnsVerticalBar() {
        Cell.LanePassCell lanePassCell = new Cell.LanePassCell();

        assertThat(lanePassCell.symbol()).isEqualTo("│");
    }

    @Test
    @DisplayName("LaneStartCell#symbol() は ┐ を返す")
    void laneStartCellSymbolReturnsTopRight() {
        Cell.LaneStartCell laneStartCell = new Cell.LaneStartCell();

        assertThat(laneStartCell.symbol()).isEqualTo("┐");
    }

    @Test
    @DisplayName("LaneJoinCell#symbol() は ┤ を返す")
    void laneJoinCellSymbolReturnsRightTee() {
        Cell.LaneJoinCell laneJoinCell = new Cell.LaneJoinCell();

        assertThat(laneJoinCell.symbol()).isEqualTo("┤");
    }

    @Test
    @DisplayName("LaneCloseCell#symbol() は ┘ を返す")
    void laneCloseCellSymbolReturnsBottomRight() {
        Cell.LaneCloseCell laneCloseCell = new Cell.LaneCloseCell();

        assertThat(laneCloseCell.symbol()).isEqualTo("┘");
    }

    @Test
    @DisplayName("LaneCrossCell#symbol() は ┼ を返す")
    void laneCrossCellSymbolReturnsCross() {
        Cell.LaneCrossCell laneCrossCell = new Cell.LaneCrossCell();

        assertThat(laneCrossCell.symbol()).isEqualTo("┼");
    }

    @Test
    @DisplayName("LaneHBarCell#symbol() は ─ を返す")
    void laneHBarCellSymbolReturnsHorizontalBar() {
        Cell.LaneHBarCell laneHBarCell = new Cell.LaneHBarCell();

        assertThat(laneHBarCell.symbol()).isEqualTo("─");
    }

    @Test
    @DisplayName("SepSpaceCell#symbol() はスペース1文字を返す")
    void sepSpaceCellSymbolReturnsSingleSpace() {
        Cell.SepSpaceCell sepSpaceCell = new Cell.SepSpaceCell();

        assertThat(sepSpaceCell.symbol()).isEqualTo(" ");
    }

    @Test
    @DisplayName("SepHBarCell#symbol() は ─ を返す")
    void sepHBarCellSymbolReturnsHorizontalBar() {
        Cell.SepHBarCell sepHBarCell = new Cell.SepHBarCell();

        assertThat(sepHBarCell.symbol()).isEqualTo("─");
    }
}
