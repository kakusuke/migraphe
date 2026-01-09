package io.github.migraphe.api.spi;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.graph.MigrationNode;
import io.github.migraphe.api.graph.NodeId;
import java.util.Set;

/**
 * MigrationNode を生成する Provider インターフェース。
 *
 * <p>プラグインはタスク定義から MigrationNode インスタンスを生成する責任を持つ。 依存関係（dependencies）はフレームワークが解決し、引数として渡す。
 */
public interface MigrationNodeProvider {

    /**
     * タスク定義から MigrationNode を生成する。
     *
     * @param nodeId ノードID
     * @param task タスク定義（name, up, down）
     * @param dependencies このノードが依存するノードID（フレームワークが解決済み）
     * @param environment ノードが属する環境
     * @return MigrationNode インスタンス
     */
    MigrationNode createNode(
            NodeId nodeId, TaskDefinition task, Set<NodeId> dependencies, Environment environment);
}
