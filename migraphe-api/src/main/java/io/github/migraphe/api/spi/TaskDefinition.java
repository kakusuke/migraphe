package io.github.migraphe.api.spi;

import java.util.Optional;

/**
 * タスク定義インターフェース。
 *
 * <p>プラグインが受け取るタスク定義を表す。依存関係（dependencies）はフレームワークが処理するため、 このインターフェースには含まれない。
 */
public interface TaskDefinition {

    /** タスク名 */
    String name();

    /** タスクの説明（オプション） */
    Optional<String> description();

    /** UP マイグレーション定義 */
    SqlDefinition up();

    /** DOWN マイグレーション定義（オプション） */
    Optional<SqlDefinition> down();

    /**
     * TaskDefinition を作成する。
     *
     * @param name タスク名
     * @param description 説明
     * @param up UP SQL 定義
     * @param down DOWN SQL 定義
     * @return TaskDefinition
     */
    static TaskDefinition of(
            String name, String description, SqlDefinition up, SqlDefinition down) {
        return new TaskDefinition() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Optional<String> description() {
                return Optional.ofNullable(description);
            }

            @Override
            public SqlDefinition up() {
                return up;
            }

            @Override
            public Optional<SqlDefinition> down() {
                return Optional.ofNullable(down);
            }
        };
    }
}
