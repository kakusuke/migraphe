package io.github.kakusuke.migraphe.api.spi;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.Set;

/**
 * MigrationNode を生成する Provider インターフェース。
 *
 * <p>プラグインはタスク定義から MigrationNode インスタンスを生成する責任を持つ。 依存関係（dependencies）はフレームワークが解決し、引数として渡す。
 *
 * @param <T> TaskDefinition の UP/DOWN アクション型
 */
public interface MigrationNodeProvider<T> {

    /**
     * タスク定義から MigrationNode を生成する。
     *
     * @param nodeId ノードID
     * @param task タスク定義（name, target, up, down）
     * @param dependencies このノードが依存するノードID（フレームワークが解決済み）
     * @param environment ノードが属する環境
     * @return MigrationNode インスタンス
     */
    MigrationNode createNode(
            NodeId nodeId,
            TaskDefinition<T> task,
            Set<NodeId> dependencies,
            Environment environment);
}
