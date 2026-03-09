package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;

/** グリッドキャンバスの各セルの種類を表す sealed interface。 */
public sealed interface Cell {
    record Node(MigrationNode node) implements Cell {}

    record Vertical() implements Cell {}

    record StreamFork() implements Cell {}

    record Fork() implements Cell {}

    record Horizontal() implements Cell {}

    record ForkToLane() implements Cell {}

    record MergePoint() implements Cell {}

    record LaneToMerge() implements Cell {}

    record ForkAndMerge() implements Cell {}

    record Empty() implements Cell {}
}
