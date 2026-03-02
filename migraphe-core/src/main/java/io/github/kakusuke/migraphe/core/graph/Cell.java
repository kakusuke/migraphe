package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.NodeId;

sealed interface Cell
        permits Cell.SpaceCell,
                Cell.TaskCell,
                Cell.VBarCell,
                Cell.HBarCell,
                Cell.ForkCell,
                Cell.MergeJoinCell,
                Cell.LaneSpaceCell,
                Cell.LanePassCell,
                Cell.LaneStartCell,
                Cell.LaneJoinCell,
                Cell.LaneCloseCell,
                Cell.LaneCrossCell,
                Cell.LaneHBarCell,
                Cell.SepSpaceCell,
                Cell.SepHBarCell {

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

    record VBarCell() implements Cell {
        public String symbol() {
            return "│";
        }
    }

    record HBarCell() implements Cell {
        public String symbol() {
            return "─";
        }
    }

    record ForkCell() implements Cell {
        public String symbol() {
            return "├";
        }
    }

    record MergeJoinCell() implements Cell {
        public String symbol() {
            return "├";
        }
    }

    record LaneSpaceCell() implements Cell {
        public String symbol() {
            return " ";
        }
    }

    record LanePassCell() implements Cell {
        public String symbol() {
            return "│";
        }
    }

    record LaneStartCell() implements Cell {
        public String symbol() {
            return "┐";
        }
    }

    record LaneJoinCell() implements Cell {
        public String symbol() {
            return "┤";
        }
    }

    record LaneCloseCell() implements Cell {
        public String symbol() {
            return "┘";
        }
    }

    record LaneCrossCell() implements Cell {
        public String symbol() {
            return "┼";
        }
    }

    record LaneHBarCell() implements Cell {
        public String symbol() {
            return "─";
        }
    }

    record SepSpaceCell() implements Cell {
        public String symbol() {
            return " ";
        }
    }

    record SepHBarCell() implements Cell {
        public String symbol() {
            return "─";
        }
    }
}
