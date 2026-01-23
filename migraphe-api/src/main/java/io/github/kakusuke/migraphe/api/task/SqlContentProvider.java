package io.github.kakusuke.migraphe.api.task;

/**
 * SQL内容を提供するタスクのマーカーインターフェース。
 *
 * <p>失敗時の詳細表示等でSQL内容を取得するために使用。
 */
public interface SqlContentProvider {

    /** 実行されるSQL内容を取得する。 */
    String sqlContent();
}
