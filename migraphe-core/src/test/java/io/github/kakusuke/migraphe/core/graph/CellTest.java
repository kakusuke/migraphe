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
}
