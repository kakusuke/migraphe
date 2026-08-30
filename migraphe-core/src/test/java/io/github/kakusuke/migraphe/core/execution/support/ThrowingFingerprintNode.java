package io.github.kakusuke.migraphe.core.execution.support;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@link MigrationNode#fingerprint(List)} が例外を投げるノードを作るためのラッパー。
 *
 * <p>プラグインの fingerprint 実装が壊れている状況を再現する。up/down タスクは委譲先のものがそのまま動くので、「適用そのものは成功したのに fingerprint
 * の取得だけが失敗する」という壊れ方だけを切り出せる。
 */
public record ThrowingFingerprintNode(MigrationNode delegate) implements DelegatingMigrationNode {

    @Override
    public @Nullable String fingerprint(List<NodeId> transitiveDependencies) {
        throw new IllegalStateException("SHA-256 is required but unavailable");
    }
}
