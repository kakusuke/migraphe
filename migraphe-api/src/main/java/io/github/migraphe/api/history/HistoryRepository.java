package io.github.migraphe.api.history;

import io.github.migraphe.api.environment.EnvironmentId;
import io.github.migraphe.api.graph.NodeId;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * マイグレーション履歴の永続化を抽象化するインターフェース。
 *
 * <p>複数の実装方式（メモリ内、PostgreSQL、ファイル、S3など）に対応するため、 履歴の保存・取得方法を抽象化します。
 */
public interface HistoryRepository {

    /** 履歴リポジトリを初期化する。 実装によっては、スキーマ作成、ファイル作成、バケット確認などを行う。 */
    void initialize();

    /** 実行記録を追加する。 */
    void record(ExecutionRecord record);

    /** 指定された環境で、指定されたノードが成功実行済みかどうかを判定する。 */
    boolean wasExecuted(NodeId nodeId, EnvironmentId environmentId);

    /** 指定された環境で成功実行済みノードのIDリストを取得する。 */
    List<NodeId> executedNodes(EnvironmentId environmentId);

    /** 指定された環境で、指定されたノードの最新の実行記録を取得する。見つからない場合は null を返す。 */
    @Nullable ExecutionRecord findLatestRecord(NodeId nodeId, EnvironmentId environmentId);

    /** 指定された環境の全ての実行記録を取得する。 */
    List<ExecutionRecord> allRecords(EnvironmentId environmentId);
}
