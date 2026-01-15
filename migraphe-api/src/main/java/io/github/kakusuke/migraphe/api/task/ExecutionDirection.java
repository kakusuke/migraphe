package io.github.kakusuke.migraphe.api.task;

/** マイグレーション実行方向。 */
public enum ExecutionDirection {
    /** Up マイグレーション（前進） */
    UP,

    /** Down マイグレーション（ロールバック） */
    DOWN
}
