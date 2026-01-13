package io.github.migraphe.api.spi;

/**
 * 環境定義インターフェース。
 *
 * <p>プラグインが提供する EnvironmentDefinition サブタイプの基底インターフェース。 各プラグインは {@code @ConfigMapping}
 * 付きのサブタイプを実装し、YAML から直接マッピングされる。
 *
 * <p>実装例:
 *
 * <pre>{@code
 * @ConfigMapping(prefix = "")
 * public interface PostgreSQLEnvironmentDefinition extends EnvironmentDefinition {
 *     @Override
 *     String type();
 *
 *     @WithName("jdbc_url")
 *     String jdbcUrl();
 *
 *     String username();
 *
 *     String password();
 * }
 * }</pre>
 */
public interface EnvironmentDefinition {

    /**
     * 環境タイプを返す。
     *
     * <p>設定ファイルで使用される型名（例: "postgresql", "mysql", "mongodb"）
     *
     * @return 環境タイプ
     */
    String type();
}
