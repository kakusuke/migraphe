package io.github.kakusuke.migraphe.core.execution.support;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.Task;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * fingerprint を返すノードを作るためのラッパー。
 *
 * <p>{@code SimpleMigrationNode} は {@link MigrationNode#fingerprint()} を override しないので常に {@code
 * null} を返す。 そのままテストに使うと fingerprint の比較が null 同士の空振りになるため、委譲しつつ fingerprint だけを差し替える。
 */
public record FingerprintedNode(MigrationNode delegate, String fingerprint)
        implements MigrationNode {
    @Override
    public NodeId id() {
        return delegate.id();
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public @Nullable String description() {
        return delegate.description();
    }

    @Override
    public Environment environment() {
        return delegate.environment();
    }

    @Override
    public Set<NodeId> dependencies() {
        return delegate.dependencies();
    }

    @Override
    public Task upTask() {
        return delegate.upTask();
    }

    @Override
    public @Nullable Task downTask() {
        return delegate.downTask();
    }
}
