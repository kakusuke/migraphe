package io.github.migraphe.core.config;

import io.smallrye.config.ConfigMapping;
import java.util.List;
import java.util.Optional;

/**
 * タスク（マイグレーションタスク）の設定。
 *
 * <p>YAMLファイル（tasks配下の.yamlファイル）からMicroProfile Configで読み込まれる。
 */
@ConfigMapping(prefix = "")
public interface TaskConfig {

    /**
     * タスク名。
     *
     * @return タスク名
     */
    String name();

    /**
     * タスクの説明（オプション）。
     *
     * @return タスクの説明（存在しない場合はempty）
     */
    Optional<String> description();

    /**
     * 実行対象のターゲットID。
     *
     * @return ターゲットID
     */
    String target();

    /**
     * 依存するタスクのIDリスト（オプション）。
     *
     * @return 依存タスクIDのリスト（存在しない場合はempty）
     */
    Optional<List<String>> dependencies();

    /**
     * UP（前進）マイグレーションのSQL定義。
     *
     * @return UP SQLブロック
     */
    SqlBlock up();

    /**
     * DOWN（ロールバック）マイグレーションのSQL定義（オプション）。
     *
     * @return DOWN SQLブロック（存在しない場合はempty）
     */
    Optional<SqlBlock> down();

    /** SQLブロック定義。 */
    interface SqlBlock {
        /**
         * 実行するSQL文。
         *
         * @return SQL文
         */
        String sql();
    }
}
