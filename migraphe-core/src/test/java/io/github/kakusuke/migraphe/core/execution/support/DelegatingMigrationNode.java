package io.github.kakusuke.migraphe.core.execution.support;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.Task;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 一部のメソッドだけを差し替えたラッパーノードを作るための委譲土台。
 *
 * <p>{@link MigrationNode} は 7 つの抽象メソッドを持つので、差し替えたい 1 つ以外を毎回書き写すと本題が埋もれる。実装側は {@link #delegate()}
 * と差し替えるメソッドだけを持てばよい。
 */
public interface DelegatingMigrationNode extends MigrationNode {

    /** 差し替えないメソッドの委譲先。 */
    MigrationNode delegate();

    @Override
    default NodeId id() {
        return delegate().id();
    }

    @Override
    default String name() {
        return delegate().name();
    }

    @Override
    default @Nullable String description() {
        return delegate().description();
    }

    @Override
    default Environment environment() {
        return delegate().environment();
    }

    @Override
    default Set<NodeId> dependencies() {
        return delegate().dependencies();
    }

    @Override
    default Task upTask() {
        return delegate().upTask();
    }

    @Override
    default @Nullable Task downTask() {
        return delegate().downTask();
    }
}
