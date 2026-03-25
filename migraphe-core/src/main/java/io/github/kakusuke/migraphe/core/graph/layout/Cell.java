package io.github.kakusuke.migraphe.core.graph.layout;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;

/** グリッドキャンバスの各セルの種類を表す sealed interface。 */
public sealed interface Cell {
    default boolean connectsUp() {
        return false;
    }

    default boolean connectsDown() {
        return false;
    }

    default boolean connectsLeft() {
        return false;
    }

    default boolean connectsRight() {
        return false;
    }

    // Structural
    record Node(MigrationNode node) implements Cell {}

    record Empty() implements Cell {}

    // Vertical flow
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
