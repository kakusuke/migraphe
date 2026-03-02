package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.NodeId;

sealed interface Cell permits Cell.SpaceCell, Cell.TaskCell, Cell.ConnectorCell {

    String symbol();

    record SpaceCell() implements Cell {
        public String symbol() {
            return " ";
        }
    }

    record TaskCell(NodeId id) implements Cell {
        public String symbol() {
            return "●";
        }
    }

    record ConnectorCell(boolean up, boolean down, boolean left, boolean right) implements Cell {
        public String symbol() {
            if (up && down && left && right) return "┼";
            if (up && down && right) return "├";
            if (up && down && left) return "┤";
            if (down && left && right) return "┬";
            if (up && left && right) return "┴";
            if (up && down) return "│";
            if (left && right) return "─";
            if (up && left) return "┘";
            if (up && right) return "└";
            if (down && left) return "┐";
            if (down && right) return "┌";
            return " ";
        }
    }
}
