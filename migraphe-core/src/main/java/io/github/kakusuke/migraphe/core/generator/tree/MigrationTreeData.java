package io.github.kakusuke.migraphe.core.generator.tree;

import java.util.List;

/** マイグレーショングラフのシリアライズ可能な表現。 */
public record MigrationTreeData(List<NodeEntry> nodes) {

    /** ノードとその依存関係。 */
    public record NodeEntry(
            String id, String name, String target, String status, List<String> dependencies) {}
}
