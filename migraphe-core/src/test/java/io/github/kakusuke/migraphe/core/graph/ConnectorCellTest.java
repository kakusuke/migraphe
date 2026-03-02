package io.github.kakusuke.migraphe.core.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cell.ConnectorCell")
class ConnectorCellTest {

    @Test
    @DisplayName("ConnectorCell は方向の組み合わせに応じて正しい罫線記号を返す")
    void shouldReturnCorrectSymbolForDirectionCombinations() {
        assertThat(new Cell.ConnectorCell(true, true, false, false).symbol()).isEqualTo("│");
        assertThat(new Cell.ConnectorCell(false, false, true, true).symbol()).isEqualTo("─");
        assertThat(new Cell.ConnectorCell(true, true, false, true).symbol()).isEqualTo("├");
        assertThat(new Cell.ConnectorCell(true, true, true, false).symbol()).isEqualTo("┤");
        assertThat(new Cell.ConnectorCell(true, false, true, false).symbol()).isEqualTo("┘");
        assertThat(new Cell.ConnectorCell(false, true, false, true).symbol()).isEqualTo("┌");
        assertThat(new Cell.ConnectorCell(false, true, true, false).symbol()).isEqualTo("┐");
        assertThat(new Cell.ConnectorCell(true, true, true, true).symbol()).isEqualTo("┼");
        assertThat(new Cell.ConnectorCell(true, false, false, true).symbol()).isEqualTo("└");
        assertThat(new Cell.ConnectorCell(false, true, true, true).symbol()).isEqualTo("┬");
        assertThat(new Cell.ConnectorCell(true, false, true, true).symbol()).isEqualTo("┴");
        assertThat(new Cell.ConnectorCell(false, false, false, false).symbol()).isEqualTo(" ");
    }
}
