package io.github.migraphe.api.spi;

import java.util.Optional;

/**
 * SQL定義インターフェース。
 *
 * <p>プラグインが受け取る SQL 定義を表す。sql、file、resource のいずれかが指定される。
 */
public interface SqlDefinition {

    /** SQL 文字列（直接指定された場合） */
    Optional<String> sql();

    /** SQL ファイルパス（ファイルから読み込む場合） */
    Optional<String> file();

    /** リソースパス（リソースから読み込む場合） */
    Optional<String> resource();

    /** いずれかの SQL ソースが指定されているか */
    default boolean isDefined() {
        return sql().isPresent() || file().isPresent() || resource().isPresent();
    }

    /**
     * SQL 定義を作成する。
     *
     * @param sql SQL 文字列
     * @param file ファイルパス
     * @param resource リソースパス
     * @return SqlDefinition
     */
    static SqlDefinition of(String sql, String file, String resource) {
        return new SqlDefinition() {
            @Override
            public Optional<String> sql() {
                return Optional.ofNullable(sql);
            }

            @Override
            public Optional<String> file() {
                return Optional.ofNullable(file);
            }

            @Override
            public Optional<String> resource() {
                return Optional.ofNullable(resource);
            }
        };
    }

    /**
     * SQL 文字列から SqlDefinition を作成する。
     *
     * @param sql SQL 文字列
     * @return SqlDefinition
     */
    static SqlDefinition ofSql(String sql) {
        return of(sql, null, null);
    }
}
