package io.github.kakusuke.migraphe.api.spi;

import java.util.List;
import java.util.Optional;

/**
 * タスク定義インターフェース。
 *
 * <p>プラグインが提供する TaskDefinition サブタイプの基底インターフェース。 各プラグインは {@code @ConfigMapping} 付きのサブタイプを実装し、YAML
 * から直接マッピングされる。
 *
 * <p>注意: SmallRye Config の {@code @ConfigMapping} はオプショナルプロパティに {@code Optional<T>} を要求するため、
 * このインターフェースは {@code Optional} を使用する。
 *
 * @param <T> UP/DOWN アクションの型（例: PostgreSQL では String（SQL文字列））
 */
public interface TaskDefinition<T> {

    /** タスク名 */
    String name();

    /** タスクの説明（オプション） */
    Optional<String> description();

    /** 実行対象のターゲットID */
    String target();

    /** 依存するタスクIDのリスト（オプション） */
    Optional<List<String>> dependencies();

    /** UP マイグレーション定義 */
    T up();

    /** DOWN マイグレーション定義（オプション） */
    Optional<T> down();
}
