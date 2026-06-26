package io.github.kakusuke.migraphe.core.graph.layout;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;

/**
 * One cell of the layout grid, describing what is drawn at a single {@code (row, col)} position.
 *
 * <p>{@code Cell} is a sealed interface with a fixed set of record variants, each representing a
 * piece of the ASCII migration tree (a node marker, a straight line, a corner, a junction, a
 * crossing, or empty space). The four {@code connects*} predicates report which of the cell's four
 * edges have a line leaving them; {@link GridCanvas} uses these predicates to auto-fill connectors
 * when inserting rows/columns and to decide which glyph to render. Every variant overrides exactly
 * the predicates that are true for its glyph; all others fall back to the {@code false} defaults
 * declared here.
 */
public sealed interface Cell {
    /**
     * Reports whether this cell has a line connecting to the cell directly above it.
     *
     * @return {@code true} if a line leaves the top edge; {@code false} by default
     */
    default boolean connectsUp() {
        return false;
    }

    /**
     * Reports whether this cell has a line connecting to the cell directly below it.
     *
     * @return {@code true} if a line leaves the bottom edge; {@code false} by default
     */
    default boolean connectsDown() {
        return false;
    }

    /**
     * Reports whether this cell has a line connecting to the cell to its left.
     *
     * @return {@code true} if a line leaves the left edge; {@code false} by default
     */
    default boolean connectsLeft() {
        return false;
    }

    /**
     * Reports whether this cell has a line connecting to the cell to its right.
     *
     * @return {@code true} if a line leaves the right edge; {@code false} by default
     */
    default boolean connectsRight() {
        return false;
    }

    // Structural

    /**
     * A cell occupied by a migration node marker (rendered as {@code ●}).
     *
     * @param node the migration node placed at this cell
     */
    record Node(MigrationNode node) implements Cell {}

    /** An empty cell that draws nothing (rendered as a space). */
    record Empty() implements Cell {}

    // Vertical flow

    /** A straight vertical line connecting the cells above and below (rendered as {@code │}). */
    record Vertical() implements Cell {
        @Override
        public boolean connectsUp() {
            return true;
        }

        @Override
        public boolean connectsDown() {
            return true;
        }
    }

    /**
     * A fork on the trunk: the line continues vertically while also branching to the right, where a
     * child stream begins (rendered as {@code ├}).
     */
    record StreamFork() implements Cell {
        @Override
        public boolean connectsUp() {
            return true;
        }

        @Override
        public boolean connectsDown() {
            return true;
        }

        @Override
        public boolean connectsRight() {
            return true;
        }
    }

    /**
     * A bottom-left corner where the line arrives from above and turns to the right (rendered as
     * {@code └}).
     */
    record Fork() implements Cell {
        @Override
        public boolean connectsUp() {
            return true;
        }

        @Override
        public boolean connectsRight() {
            return true;
        }
    }

    // Horizontal flow

    /**
     * A straight horizontal line connecting the cells to its left and right (rendered as {@code
     * ─}).
     */
    record Horizontal() implements Cell {
        @Override
        public boolean connectsLeft() {
            return true;
        }

        @Override
        public boolean connectsRight() {
            return true;
        }
    }

    // Fork-side lane routing

    /**
     * A top-right corner at a non-tree-edge source: the horizontal connector arrives from the left
     * and turns downward into a routing lane (rendered as {@code ┐}).
     */
    record ForkToLane() implements Cell {
        @Override
        public boolean connectsDown() {
            return true;
        }

        @Override
        public boolean connectsLeft() {
            return true;
        }
    }

    /**
     * A T-junction where a non-tree-edge connector both turns down into a lane and continues to the
     * right toward another lane (rendered as {@code ┬}).
     */
    record ForkAndMerge() implements Cell {
        @Override
        public boolean connectsDown() {
            return true;
        }

        @Override
        public boolean connectsLeft() {
            return true;
        }

        @Override
        public boolean connectsRight() {
            return true;
        }
    }

    /**
     * A top-left corner where a line starts and turns to the right and down (rendered as {@code
     * ┌}); used when inserting a merge row whose cell above does not already connect down.
     */
    record DownRight() implements Cell {
        @Override
        public boolean connectsDown() {
            return true;
        }

        @Override
        public boolean connectsRight() {
            return true;
        }
    }

    // Merge-side lane routing

    /**
     * A right-side T-junction where a routing lane passes vertically through and a connector
     * arrives from the left to merge into it (rendered as {@code ┤}).
     */
    record MergePoint() implements Cell {
        @Override
        public boolean connectsUp() {
            return true;
        }

        @Override
        public boolean connectsDown() {
            return true;
        }

        @Override
        public boolean connectsLeft() {
            return true;
        }
    }

    /**
     * A bottom-right corner at a non-tree-edge merge row: a routing lane arrives from above and
     * turns left toward the target node (rendered as {@code ┘}).
     */
    record LaneToMerge() implements Cell {
        @Override
        public boolean connectsUp() {
            return true;
        }

        @Override
        public boolean connectsLeft() {
            return true;
        }
    }

    /**
     * A bottom T-junction on a merge row where the horizontal connector continues to the left while
     * a lane joins from above (rendered as {@code ┴}).
     */
    record MergeJunction() implements Cell {
        @Override
        public boolean connectsUp() {
            return true;
        }

        @Override
        public boolean connectsLeft() {
            return true;
        }

        @Override
        public boolean connectsRight() {
            return true;
        }
    }

    // Crossings

    /**
     * A four-way crossing of a vertical line and a horizontal connector that do not join (rendered
     * as {@code │}, i.e. the vertical line is drawn over the crossing).
     */
    record CrossPoint() implements Cell {
        @Override
        public boolean connectsUp() {
            return true;
        }

        @Override
        public boolean connectsDown() {
            return true;
        }

        @Override
        public boolean connectsLeft() {
            return true;
        }

        @Override
        public boolean connectsRight() {
            return true;
        }
    }

    /**
     * A four-way junction where a vertical lane and a horizontal connector meet and merge (rendered
     * as {@code ┼}).
     */
    record CrossMerge() implements Cell {
        @Override
        public boolean connectsUp() {
            return true;
        }

        @Override
        public boolean connectsDown() {
            return true;
        }

        @Override
        public boolean connectsLeft() {
            return true;
        }

        @Override
        public boolean connectsRight() {
            return true;
        }
    }
}
