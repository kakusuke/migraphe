package io.github.kakusuke.migraphe.core.execution.support;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;

/**
 * fingerprint を返すノードを作るためのラッパー。
 *
 * <p>{@code SimpleMigrationNode} は {@link MigrationNode#fingerprint(List)} を override しないので常に
 * {@code null} を返す。 そのままテストに使うと fingerprint の比較が null 同士の空振りになるため、委譲しつつ fingerprint だけを差し替える。
 */
public record FingerprintedNode(MigrationNode delegate, String fingerprint)
        implements DelegatingMigrationNode {

    @Override
    public String fingerprint(List<NodeId> transitiveDependencies) {
        return fingerprint;
    }
}
