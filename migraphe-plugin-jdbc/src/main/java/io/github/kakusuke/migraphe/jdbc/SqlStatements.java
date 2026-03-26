package io.github.kakusuke.migraphe.jdbc;

import java.util.Arrays;

/** SQL文分割ユーティリティ。 */
public final class SqlStatements {

    private SqlStatements() {}

    /**
     * SQL テキストをステートメントに分割する。
     *
     * <p>セミコロン + 空白/コメント + 改行 のパターンで分割。 文字列リテラル内のセミコロンで誤分割しないよう、行末のみを対象とする。
     */
    public static String[] splitStatements(String sql) {
        String[] parts = sql.split(";\\s*?(--[^\\n]*)?\\r?\\n");
        return Arrays.stream(parts)
                .map(String::trim)
                .map(s -> s.endsWith(";") ? s.substring(0, s.length() - 1).trim() : s)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }
}
