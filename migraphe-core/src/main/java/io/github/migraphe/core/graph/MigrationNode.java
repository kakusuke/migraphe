package io.github.migraphe.core.graph;

import io.github.migraphe.core.environment.Environment;
import io.github.migraphe.core.task.Task;
import java.util.Optional;
import java.util.Set;

/** マイグレーショングラフのノード（タスク）インターフェース。 プラグインがこのインターフェースを実装して、具体的なマイグレーションタスクを定義する。 */
public interface MigrationNode {

    /** ノードの一意識別子 */
    NodeId id();

    /** ノードの名前 */
    String name();

    /** ノードの説明（オプション） */
    String description();

    /** このノードが属する環境 */
    Environment environment();

    /** このノードが依存するノードのID集合 */
    Set<NodeId> dependencies();

    /** Up マイグレーション（前進） */
    Task upTask();

    /** Down マイグレーション（ロールバック） ロールバック非対応の場合は Optional.empty() */
    Optional<Task> downTask();

    /** 依存関係がないか（ルートノードか） */
    default boolean hasNoDependencies() {
        return dependencies().isEmpty();
    }

    /** 指定されたノードに依存しているか */
    default boolean dependsOn(NodeId nodeId) {
        return dependencies().contains(nodeId);
    }
}
