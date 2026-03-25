package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** マイグレーション実行のインターフェース。 */
public interface Executor {

    /**
     * 実行対象ノードを決定する。
     *
     * @param targetId 特定のターゲットID（null の場合は全ノード）
     * @return 未実行のノードIDセット
     */
    Set<NodeId> determineTargetNodes(@Nullable NodeId targetId);

    /**
     * マイグレーションを実行する。
     *
     * @param targetNodes 実行対象ノード
     * @return 実行結果
     */
    ExecutionResult execute(Set<NodeId> targetNodes);
}
