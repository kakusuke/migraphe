package io.github.kakusuke.migraphe.jdbc.statement;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** セミコロン区切りの SQL テキストを個々のステートメントに分割するクラス。 */
public final class StatementSplitter {

    private final SqlParser region;
    private final SqlParser trivia;
    private final String delimiter;
    private final @Nullable DelimiterDirective directive;

    /**
     * 区切り文字・領域パーサー・トリビアパーサーを指定してインスタンスを構築する。 方言が独自の領域（例: PostgreSQL のドル引用符）を組み込む際に使用する。
     *
     * @param region スキップする領域（クォート・コメント等）を消費するパーサー
     * @param trivia セグメント先頭で読み飛ばす空白・コメントを消費するパーサー
     * @param delimiter ステートメントの区切り文字
     */
    public StatementSplitter(SqlParser region, SqlParser trivia, char delimiter) {
        this(region, trivia, String.valueOf(delimiter), null);
    }

    /**
     * 区切り文字列・領域パーサー・トリビアパーサー・区切り変更ディレクティブを指定して構築する。 多文字区切りや実行中の区切り変更（例: MySQL の {@code
     * DELIMITER}）に対応する。
     *
     * @param region スキップする領域（クォート・コメント等）を消費するパーサー
     * @param trivia セグメント先頭で読み飛ばす空白・コメントを消費するパーサー
     * @param delimiter ステートメントの初期区切り文字列
     * @param directive 区切り変更指示の検出器（不要なら {@code null}）
     */
    public StatementSplitter(
            SqlParser region,
            SqlParser trivia,
            String delimiter,
            @Nullable DelimiterDirective directive) {
        this.region = region;
        this.trivia = trivia;
        this.delimiter = delimiter;
        this.directive = directive;
    }

    /** 標準的な引用・コメント領域を認識し、{@code ';'} を区切りとするインスタンスを返す。 セグメント先頭の空白・行コメント・ブロックコメントはトリビアとして読み飛ばす。 */
    public static StatementSplitter standard() {
        SqlParser trivia =
                SqlParsers.many(
                        SqlParsers.or(
                                SqlParsers.whitespace(),
                                SqlParsers.lineComment("--", false),
                                SqlParsers.delimited("/*", "*/")));
        return new StatementSplitter(SqlParsers.standardRegion(), trivia, ';');
    }

    /**
     * SQL テキストを区切り文字で分割し、各ステートメントを trim して返す。 空になったセグメントは除外する。区切り文字自体は結果に含まれない。
     * 各セグメント先頭のトリビア（空白・コメント）は含まれない。区切り変更ディレクティブが指定された場合、 セグメント先頭で検出された指示は出力に含めず、以降の区切り文字を切り替える。
     */
    public List<String> split(String sql) {
        List<String> result = new ArrayList<>();
        int len = sql.length();
        String delim = delimiter;
        int start = beginSegment(sql, 0);
        // ディレクティブ適用で区切りが変わる可能性があるため、初期位置でも判定する。
        Directive applied = applyDirective(sql, start, delim);
        delim = applied.delimiter();
        start = applied.pos();
        int pos = start;
        while (pos < len) {
            int next = region.parse(sql, pos);
            if (next >= 0) {
                pos = next;
                continue;
            }
            if (sql.startsWith(delim, pos)) {
                String segment = sql.substring(start, pos).trim();
                if (!segment.isEmpty()) {
                    result.add(segment);
                }
                int newStart = beginSegment(sql, pos + delim.length());
                Directive d = applyDirective(sql, newStart, delim);
                delim = d.delimiter();
                start = d.pos();
                pos = start;
            } else {
                pos++;
            }
        }
        String tail = sql.substring(start).trim();
        if (!tail.isEmpty()) {
            result.add(tail);
        }
        return result;
    }

    /** トリビアをスキップした後、可能なら区切り変更ディレクティブを連続適用してセグメント先頭位置を確定する。 */
    private int beginSegment(String sql, int pos) {
        return skipTrivia(sql, pos);
    }

    /** {@code pos} で区切り変更ディレクティブを連続適用し、確定した区切り文字と次セグメント先頭位置を返す。 */
    private Directive applyDirective(String sql, int pos, String delim) {
        if (directive == null) {
            return new Directive(delim, pos);
        }
        String currentDelim = delim;
        int cur = pos;
        while (true) {
            DelimiterDirective.@Nullable Result r = directive.detect(sql, cur);
            if (r == null) {
                return new Directive(currentDelim, cur);
            }
            currentDelim = r.newDelimiter();
            cur = skipTrivia(sql, r.nextPos());
        }
    }

    private int skipTrivia(String sql, int pos) {
        int t = trivia.parse(sql, pos);
        return t > pos ? t : pos;
    }

    /** 区切り変更ディレクティブ適用後の区切り文字と位置を保持する内部レコード。 */
    private record Directive(String delimiter, int pos) {}
}
