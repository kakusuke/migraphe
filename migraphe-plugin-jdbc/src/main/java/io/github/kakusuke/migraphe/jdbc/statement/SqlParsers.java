package io.github.kakusuke.migraphe.jdbc.statement;

/** {@link SqlParser} のファクトリーメソッド集。 */
public final class SqlParsers {

    private SqlParsers() {}

    /** pos の文字が空白なら {@code pos + 1}、そうでなければ -1 を返すパーサー。 */
    public static SqlParser whitespace() {
        return (sql, pos) ->
                pos < sql.length() && Character.isWhitespace(sql.charAt(pos)) ? pos + 1 : -1;
    }

    /** pos から {@code token} に完全一致したら {@code pos + token.length()} を返すパーサー。 非マッチは -1。 */
    public static SqlParser literal(String token) {
        return (sql, pos) -> sql.startsWith(token, pos) ? pos + token.length() : -1;
    }

    /** 各パーサーを順に適用し、全成功なら最後の pos を返す。途中で -1 なら即 -1。 */
    public static SqlParser seq(SqlParser... parsers) {
        return (sql, pos) -> {
            int p = pos;
            for (SqlParser parser : parsers) {
                p = parser.parse(sql, p);
                if (p < 0) return -1;
            }
            return p;
        };
    }

    /** 各パーサーを先頭から試し、最初に成功（-1 以外）したパーサーの結果を返す。 全て失敗なら -1。 */
    public static SqlParser or(SqlParser... parsers) {
        return (sql, pos) -> {
            for (SqlParser parser : parsers) {
                int p = parser.parse(sql, pos);
                if (p >= 0) return p;
            }
            return -1;
        };
    }

    /** pos に文字があれば 1 文字消費して {@code pos + 1}、終端なら -1。 */
    public static SqlParser anyChar() {
        return (sql, pos) -> pos < sql.length() ? pos + 1 : -1;
    }

    /** 負の先読み。{@code p} が失敗（-1）なら消費せず pos を返し、成功（>=0）なら -1。 */
    public static SqlParser not(SqlParser p) {
        return (sql, pos) -> p.parse(sql, pos) < 0 ? pos : -1;
    }

    /** {@code p} を 0 回以上貪欲に繰り返し、常に成功して最遠の pos を返す。 {@code p} が pos を進めずに成功した場合は無限ループ回避のため停止する。 */
    public static SqlParser many(SqlParser p) {
        return (sql, pos) -> {
            int cur = pos;
            while (true) {
                int next = p.parse(sql, cur);
                if (next < 0 || next == cur) return cur;
                cur = next;
            }
        };
    }

    /** {@code p} が成功すればその pos、失敗すれば消費せず pos を返す。 */
    public static SqlParser opt(SqlParser p) {
        return (sql, pos) -> {
            int next = p.parse(sql, pos);
            return next < 0 ? pos : next;
        };
    }

    /**
     * 大文字小文字を無視して {@code word} に語境界で一致する場合のみ消費後 pos を返す。 語境界は直前の文字（pos &gt; 0
     * のとき）と一致直後の文字がいずれも識別子文字 （{@link Character#isLetterOrDigit} または {@code '_'}）でないこと。非一致は -1。
     */
    public static SqlParser keyword(String word) {
        int len = word.length();
        return (sql, pos) -> {
            if (!sql.regionMatches(true, pos, word, 0, len)) return -1;
            if (pos > 0 && isIdentifierChar(sql.charAt(pos - 1))) return -1;
            int end = pos + len;
            if (end < sql.length() && isIdentifierChar(sql.charAt(end))) return -1;
            return end;
        };
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** 遅延参照。呼び出し時に {@code supplier} から解決したパーサーへ委譲する。 相互再帰の前方参照解決に用いる。 */
    public static SqlParser ref(java.util.function.Supplier<SqlParser> supplier) {
        return (sql, pos) -> supplier.get().parse(sql, pos);
    }

    /**
     * {@code quote} で始まる引用領域を消費する。開始が {@code quote} でなければ -1。 {@code doubling} が true なら {@code
     * quote} の 2 連続をエスケープとして 2 文字スキップ、 {@code backslashEscape} が true なら {@code '\'} の次の 1 文字を 2
     * 文字スキップする。 閉じ {@code quote} の次の pos を返す。閉じが見つからず終端に達したら {@code sql.length()}（未終端は終端まで 1
     * 領域とみなす）。
     */
    public static SqlParser quoted(char quote, boolean doubling, boolean backslashEscape) {
        return (sql, pos) -> {
            int len = sql.length();
            if (pos >= len || sql.charAt(pos) != quote) return -1;
            int i = pos + 1;
            while (i < len) {
                char c = sql.charAt(i);
                if (backslashEscape && c == '\\' && i + 1 < len) {
                    i += 2;
                    continue;
                }
                if (c == quote) {
                    if (doubling && i + 1 < len && sql.charAt(i + 1) == quote) {
                        i += 2;
                        continue;
                    }
                    return i + 1;
                }
                i++;
            }
            return len;
        };
    }

    /**
     * {@code prefix} で始まる行コメントを消費する。{@code prefix} で始まらなければ -1。 {@code requireSpaceAfter} が true
     * の場合、{@code prefix} 直後が空白 （{@link Character#isWhitespace}）か終端でなければ -1（MySQL の {@code "-- "}
     * 要件）。 一致したら次の {@code '\n'} の手前までの pos を返す。{@code '\n'} が無ければ {@code sql.length()}。
     */
    public static SqlParser lineComment(String prefix, boolean requireSpaceAfter) {
        int len = prefix.length();
        return (sql, pos) -> {
            if (!sql.startsWith(prefix, pos)) return -1;
            int after = pos + len;
            if (requireSpaceAfter
                    && after < sql.length()
                    && !Character.isWhitespace(sql.charAt(after))) {
                return -1;
            }
            int nl = sql.indexOf('\n', after);
            return nl < 0 ? sql.length() : nl;
        };
    }

    /**
     * {@code open} で始まる区切り領域を消費する。{@code open} で始まらなければ -1。 {@code open} 消費後に {@code close}
     * を探し、見つかれば {@code close} を含めた次の pos を返す。 見つからなければ {@code sql.length()}（未終端は終端まで 1 領域とみなす）。
     */
    public static SqlParser delimited(String open, String close) {
        return (sql, pos) -> {
            if (!sql.startsWith(open, pos)) return -1;
            int from = pos + open.length();
            int idx = sql.indexOf(close, from);
            return idx < 0 ? sql.length() : idx + close.length();
        };
    }

    /**
     * 標準的な引用・コメント領域をまとめて消費するパーサー。 シングルクォート文字列・ダブルクォート識別子（いずれも二重化エスケープ）・ {@code --} 行コメント・{@code
     * /*}〜{@code *}{@code /} ブロックコメントを試す。
     */
    public static SqlParser standardRegion() {
        return or(
                quoted('\'', true, false),
                quoted('"', true, false),
                lineComment("--", false),
                delimited("/*", "*/"));
    }
}
