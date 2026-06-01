package io.github.kakusuke.migraphe.postgresql.statement;

import io.github.kakusuke.migraphe.jdbc.statement.SqlParser;
import io.github.kakusuke.migraphe.jdbc.statement.SqlParsers;
import io.github.kakusuke.migraphe.jdbc.statement.StatementSplitter;

/** PostgreSQL 方言の SQL 分割文法を提供するファクトリー。 */
public final class PostgreSqlGrammar {

    private PostgreSqlGrammar() {}

    /**
     * PostgreSQL のドル引用符 {@code $tag$ ... $tag$} を 1 領域として消費するパーサーを返す。
     *
     * <p>開始位置が {@code $} で始まり、{@code $}〜{@code $} の間がタグ（{@code [A-Za-z_][A-Za-z0-9_]*} もしくは空タグ
     * {@code $$}）として解釈できる場合のみマッチする。開始タグと同一の閉じタグが現れるまで、 タグ内の任意文字・改行・{@code ;} をすべて含めて消費し、閉じタグ直後の pos
     * を返す。閉じタグが 見つからなければ {@code sql.length()}（未終端は終端まで 1 領域とみなす）。
     *
     * <p>{@code $1} のような数字始まりのパラメータプレースホルダはドルタグではないため -1 を返し、他の パーサーへ委ねる。
     *
     * @return ドル引用符パーサー
     */
    public static SqlParser dollarQuoted() {
        return (sql, pos) -> {
            int len = sql.length();
            if (pos >= len || sql.charAt(pos) != '$') {
                return -1;
            }
            int tagEnd = readTag(sql, pos);
            if (tagEnd < 0) {
                return -1;
            }
            String openTag = sql.substring(pos, tagEnd);
            int idx = sql.indexOf(openTag, tagEnd);
            return idx < 0 ? len : idx + openTag.length();
        };
    }

    /** pos の {@code '$'} から始まるドルタグの終端（閉じ {@code '$'} の次の位置）を返す。 ドルタグとして解釈できなければ -1。 */
    private static int readTag(String sql, int pos) {
        int len = sql.length();
        // pos は '$' であることが呼び出し側で保証されている。
        int i = pos + 1;
        // 空タグ $$ を許容する。
        while (i < len) {
            char c = sql.charAt(i);
            if (c == '$') {
                return i + 1;
            }
            boolean valid = i == pos + 1 ? (Character.isLetter(c) || c == '_') : isTagChar(c);
            if (!valid) {
                return -1;
            }
            i++;
        }
        return -1;
    }

    private static boolean isTagChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * PostgreSQL 方言の {@link StatementSplitter} を返す。
     *
     * <p>領域はドル引用符と標準引用・コメント領域、トリビアは空白・行コメント・ブロックコメント、 区切り文字は {@code ';'}。手続き本体は常にドル引用符／文字列内に
     * 収まるため、キーワードブロックや DELIMITER 文法は含めない。
     *
     * @return PostgreSQL 用ステートメント分割器
     */
    public static StatementSplitter splitter() {
        SqlParser region = SqlParsers.or(dollarQuoted(), SqlParsers.standardRegion());
        SqlParser trivia =
                SqlParsers.many(
                        SqlParsers.or(
                                SqlParsers.whitespace(),
                                SqlParsers.lineComment("--", false),
                                SqlParsers.delimited("/*", "*/")));
        return new StatementSplitter(region, trivia, ';');
    }
}
