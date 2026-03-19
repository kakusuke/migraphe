package io.github.kakusuke.migraphe.core.graph;

import static io.github.kakusuke.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Cell")
class CellTest {

    @Nested
    @DisplayName("connectsUp()")
    class ConnectsUpTest {

        @Test
        @DisplayName("Node は上方向に接続しない")
        void nodeShouldNotConnectUp() {
            MigrationNode n = node("x").build();
            assertThat(new Cell.Node(n).connectsUp()).isFalse();
        }

        @Test
        @DisplayName("Empty は上方向に接続しない")
        void emptyShouldNotConnectUp() {
            assertThat(new Cell.Empty().connectsUp()).isFalse();
        }

        @Test
        @DisplayName("Vertical は上方向に接続する")
        void verticalShouldConnectUp() {
            assertThat(new Cell.Vertical().connectsUp()).isTrue();
        }

        @Test
        @DisplayName("StreamFork は上方向に接続する")
        void streamForkShouldConnectUp() {
            assertThat(new Cell.StreamFork().connectsUp()).isTrue();
        }

        @Test
        @DisplayName("Fork は上方向に接続する")
        void forkShouldConnectUp() {
            assertThat(new Cell.Fork().connectsUp()).isTrue();
        }

        @Test
        @DisplayName("Horizontal は上方向に接続しない")
        void horizontalShouldNotConnectUp() {
            assertThat(new Cell.Horizontal().connectsUp()).isFalse();
        }

        @Test
        @DisplayName("ForkToLane は上方向に接続しない")
        void forkToLaneShouldNotConnectUp() {
            assertThat(new Cell.ForkToLane().connectsUp()).isFalse();
        }

        @Test
        @DisplayName("ForkAndMerge は上方向に接続しない")
        void forkAndMergeShouldNotConnectUp() {
            assertThat(new Cell.ForkAndMerge().connectsUp()).isFalse();
        }

        @Test
        @DisplayName("DownRight は上方向に接続しない")
        void downRightShouldNotConnectUp() {
            assertThat(new Cell.DownRight().connectsUp()).isFalse();
        }

        @Test
        @DisplayName("MergePoint は上方向に接続する")
        void mergePointShouldConnectUp() {
            assertThat(new Cell.MergePoint().connectsUp()).isTrue();
        }

        @Test
        @DisplayName("LaneToMerge は上方向に接続する")
        void laneToMergeShouldConnectUp() {
            assertThat(new Cell.LaneToMerge().connectsUp()).isTrue();
        }

        @Test
        @DisplayName("MergeJunction は上方向に接続する")
        void mergeJunctionShouldConnectUp() {
            assertThat(new Cell.MergeJunction().connectsUp()).isTrue();
        }

        @Test
        @DisplayName("CrossPoint は上方向に接続する")
        void crossPointShouldConnectUp() {
            assertThat(new Cell.CrossPoint().connectsUp()).isTrue();
        }

        @Test
        @DisplayName("CrossMerge は上方向に接続する")
        void crossMergeShouldConnectUp() {
            assertThat(new Cell.CrossMerge().connectsUp()).isTrue();
        }
    }

    @Nested
    @DisplayName("connectsDown()")
    class ConnectsDownTest {

        @Test
        @DisplayName("Node は下方向に接続しない")
        void nodeShouldNotConnectDown() {
            MigrationNode n = node("x").build();
            assertThat(new Cell.Node(n).connectsDown()).isFalse();
        }

        @Test
        @DisplayName("Empty は下方向に接続しない")
        void emptyShouldNotConnectDown() {
            assertThat(new Cell.Empty().connectsDown()).isFalse();
        }

        @Test
        @DisplayName("Vertical は下方向に接続する")
        void verticalShouldConnectDown() {
            assertThat(new Cell.Vertical().connectsDown()).isTrue();
        }

        @Test
        @DisplayName("StreamFork は下方向に接続する")
        void streamForkShouldConnectDown() {
            assertThat(new Cell.StreamFork().connectsDown()).isTrue();
        }

        @Test
        @DisplayName("Fork は下方向に接続しない")
        void forkShouldNotConnectDown() {
            assertThat(new Cell.Fork().connectsDown()).isFalse();
        }

        @Test
        @DisplayName("Horizontal は下方向に接続しない")
        void horizontalShouldNotConnectDown() {
            assertThat(new Cell.Horizontal().connectsDown()).isFalse();
        }

        @Test
        @DisplayName("ForkToLane は下方向に接続する")
        void forkToLaneShouldConnectDown() {
            assertThat(new Cell.ForkToLane().connectsDown()).isTrue();
        }

        @Test
        @DisplayName("ForkAndMerge は下方向に接続する")
        void forkAndMergeShouldConnectDown() {
            assertThat(new Cell.ForkAndMerge().connectsDown()).isTrue();
        }

        @Test
        @DisplayName("DownRight は下方向に接続する")
        void downRightShouldConnectDown() {
            assertThat(new Cell.DownRight().connectsDown()).isTrue();
        }

        @Test
        @DisplayName("MergePoint は下方向に接続する")
        void mergePointShouldConnectDown() {
            assertThat(new Cell.MergePoint().connectsDown()).isTrue();
        }

        @Test
        @DisplayName("LaneToMerge は下方向に接続しない")
        void laneToMergeShouldNotConnectDown() {
            assertThat(new Cell.LaneToMerge().connectsDown()).isFalse();
        }

        @Test
        @DisplayName("MergeJunction は下方向に接続しない")
        void mergeJunctionShouldNotConnectDown() {
            assertThat(new Cell.MergeJunction().connectsDown()).isFalse();
        }

        @Test
        @DisplayName("CrossPoint は下方向に接続する")
        void crossPointShouldConnectDown() {
            assertThat(new Cell.CrossPoint().connectsDown()).isTrue();
        }

        @Test
        @DisplayName("CrossMerge は下方向に接続する")
        void crossMergeShouldConnectDown() {
            assertThat(new Cell.CrossMerge().connectsDown()).isTrue();
        }
    }

    @Nested
    @DisplayName("connectsLeft()")
    class ConnectsLeftTest {

        @Test
        @DisplayName("Node は左方向に接続しない")
        void nodeShouldNotConnectLeft() {
            MigrationNode n = node("x").build();
            assertThat(new Cell.Node(n).connectsLeft()).isFalse();
        }

        @Test
        @DisplayName("Empty は左方向に接続しない")
        void emptyShouldNotConnectLeft() {
            assertThat(new Cell.Empty().connectsLeft()).isFalse();
        }

        @Test
        @DisplayName("Vertical は左方向に接続しない")
        void verticalShouldNotConnectLeft() {
            assertThat(new Cell.Vertical().connectsLeft()).isFalse();
        }

        @Test
        @DisplayName("StreamFork は左方向に接続しない")
        void streamForkShouldNotConnectLeft() {
            assertThat(new Cell.StreamFork().connectsLeft()).isFalse();
        }

        @Test
        @DisplayName("Fork は左方向に接続しない")
        void forkShouldNotConnectLeft() {
            assertThat(new Cell.Fork().connectsLeft()).isFalse();
        }

        @Test
        @DisplayName("Horizontal は左方向に接続する")
        void horizontalShouldConnectLeft() {
            assertThat(new Cell.Horizontal().connectsLeft()).isTrue();
        }

        @Test
        @DisplayName("ForkToLane は左方向に接続する")
        void forkToLaneShouldConnectLeft() {
            assertThat(new Cell.ForkToLane().connectsLeft()).isTrue();
        }

        @Test
        @DisplayName("ForkAndMerge は左方向に接続する")
        void forkAndMergeShouldConnectLeft() {
            assertThat(new Cell.ForkAndMerge().connectsLeft()).isTrue();
        }

        @Test
        @DisplayName("DownRight は左方向に接続しない")
        void downRightShouldNotConnectLeft() {
            assertThat(new Cell.DownRight().connectsLeft()).isFalse();
        }

        @Test
        @DisplayName("MergePoint は左方向に接続する")
        void mergePointShouldConnectLeft() {
            assertThat(new Cell.MergePoint().connectsLeft()).isTrue();
        }

        @Test
        @DisplayName("LaneToMerge は左方向に接続する")
        void laneToMergeShouldConnectLeft() {
            assertThat(new Cell.LaneToMerge().connectsLeft()).isTrue();
        }

        @Test
        @DisplayName("MergeJunction は左方向に接続する")
        void mergeJunctionShouldConnectLeft() {
            assertThat(new Cell.MergeJunction().connectsLeft()).isTrue();
        }

        @Test
        @DisplayName("CrossPoint は左方向に接続する")
        void crossPointShouldConnectLeft() {
            assertThat(new Cell.CrossPoint().connectsLeft()).isTrue();
        }

        @Test
        @DisplayName("CrossMerge は左方向に接続する")
        void crossMergeShouldConnectLeft() {
            assertThat(new Cell.CrossMerge().connectsLeft()).isTrue();
        }
    }

    @Nested
    @DisplayName("connectsRight()")
    class ConnectsRightTest {

        @Test
        @DisplayName("Node は右方向に接続しない")
        void nodeShouldNotConnectRight() {
            MigrationNode n = node("x").build();
            assertThat(new Cell.Node(n).connectsRight()).isFalse();
        }

        @Test
        @DisplayName("Empty は右方向に接続しない")
        void emptyShouldNotConnectRight() {
            assertThat(new Cell.Empty().connectsRight()).isFalse();
        }

        @Test
        @DisplayName("Vertical は右方向に接続しない")
        void verticalShouldNotConnectRight() {
            assertThat(new Cell.Vertical().connectsRight()).isFalse();
        }

        @Test
        @DisplayName("StreamFork は右方向に接続する")
        void streamForkShouldConnectRight() {
            assertThat(new Cell.StreamFork().connectsRight()).isTrue();
        }

        @Test
        @DisplayName("Fork は右方向に接続する")
        void forkShouldConnectRight() {
            assertThat(new Cell.Fork().connectsRight()).isTrue();
        }

        @Test
        @DisplayName("Horizontal は右方向に接続する")
        void horizontalShouldConnectRight() {
            assertThat(new Cell.Horizontal().connectsRight()).isTrue();
        }

        @Test
        @DisplayName("ForkToLane は右方向に接続しない")
        void forkToLaneShouldNotConnectRight() {
            assertThat(new Cell.ForkToLane().connectsRight()).isFalse();
        }

        @Test
        @DisplayName("ForkAndMerge は右方向に接続する")
        void forkAndMergeShouldConnectRight() {
            assertThat(new Cell.ForkAndMerge().connectsRight()).isTrue();
        }

        @Test
        @DisplayName("DownRight は右方向に接続する")
        void downRightShouldConnectRight() {
            assertThat(new Cell.DownRight().connectsRight()).isTrue();
        }

        @Test
        @DisplayName("MergePoint は右方向に接続しない")
        void mergePointShouldNotConnectRight() {
            assertThat(new Cell.MergePoint().connectsRight()).isFalse();
        }

        @Test
        @DisplayName("LaneToMerge は右方向に接続しない")
        void laneToMergeShouldNotConnectRight() {
            assertThat(new Cell.LaneToMerge().connectsRight()).isFalse();
        }

        @Test
        @DisplayName("MergeJunction は右方向に接続する")
        void mergeJunctionShouldConnectRight() {
            assertThat(new Cell.MergeJunction().connectsRight()).isTrue();
        }

        @Test
        @DisplayName("CrossPoint は右方向に接続する")
        void crossPointShouldConnectRight() {
            assertThat(new Cell.CrossPoint().connectsRight()).isTrue();
        }

        @Test
        @DisplayName("CrossMerge は右方向に接続する")
        void crossMergeShouldConnectRight() {
            assertThat(new Cell.CrossMerge().connectsRight()).isTrue();
        }
    }
}
