package io.github.kakusuke.migraphe.core.graph.layout;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;

/** グラフ描画時の各ノードの行情報。 */
public record NodeLineInfo(MigrationNode node, int column) {}
