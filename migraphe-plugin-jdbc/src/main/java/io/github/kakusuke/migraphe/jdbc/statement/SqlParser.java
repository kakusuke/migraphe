package io.github.kakusuke.migraphe.jdbc.statement;

/** SQL テキストの一部を pos から消費するパーサー。 */
@FunctionalInterface
public interface SqlParser {

    /**
     * pos から解析を試みる。
     *
     * @return マッチした場合は消費後の pos、非マッチは -1
     */
    int parse(String sql, int pos);
}
