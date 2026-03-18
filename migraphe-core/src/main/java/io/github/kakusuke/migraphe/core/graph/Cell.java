package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;

/** グリッドキャンバスの各セルの種類を表す sealed interface。 */
public sealed interface Cell {
    // Structural
    record Node(MigrationNode node) implements Cell {}

    record Empty() implements Cell {}

    // Vertical flow
    record Vertical() implements Cell {}

    record StreamFork() implements Cell {}

    record Fork() implements Cell {}

    // Horizontal flow
    record Horizontal() implements Cell {}

    // Fork-side lane routing
    record ForkToLane() implements Cell {}

    record ForkAndMerge() implements Cell {}

    record DownRight() implements Cell {}

    // Merge-side lane routing
    record MergePoint() implements Cell {}

    record LaneToMerge() implements Cell {}

    record MergeJunction() implements Cell {}

    // Crossings
    record CrossPoint() implements Cell {}

    record CrossMerge() implements Cell {}
}
