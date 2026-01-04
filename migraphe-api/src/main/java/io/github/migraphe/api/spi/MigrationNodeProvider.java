package io.github.migraphe.api.spi;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.graph.MigrationNode;
import io.github.migraphe.api.graph.NodeId;
import java.util.Map;

/**
 * MigrationNode を生成する Provider インターフェース。
 *
 * <p>プラグインはタスク設定から MigrationNode インスタンスを生成する責任を持つ。
 */
public interface MigrationNodeProvider {

    /**
     * タスク設定から MigrationNode を生成する。
     *
     * @param nodeId ノードID
     * @param taskConfig タスク設定（YAML から読み込まれた設定マップ）
     * @param environment ノードが属する環境
     * @return MigrationNode インスタンス
     */
    MigrationNode createNode(
            NodeId nodeId, Map<String, Object> taskConfig, Environment environment);
}
