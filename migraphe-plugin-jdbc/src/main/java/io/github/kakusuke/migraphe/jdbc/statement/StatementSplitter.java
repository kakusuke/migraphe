package io.github.kakusuke.migraphe.jdbc.statement;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** セミコロン区切りの SQL テキストを個々のステートメントに分割するクラス。 */
public final class StatementSplitter {

    private final SqlParser region;
    private final String delimiter;
    private final @Nullable DelimiterDirective directive;

    /**
     * 区切り文字・領域パーサーを指定してインスタンスを構築する。 方言が独自の領域（例: PostgreSQL のドル引用符）を組み込む際に使用する。
     *
     * @param region スキップする領域（クォート・コメント等）を消費するパーサー
     * @param delimiter ステートメントの区切り文字
     */
    public StatementSplitter(SqlParser region, char delimiter) {
        this(region, String.valueOf(delimiter), null);
    }

    /**
     * 区切り文字列・領域パーサー・区切り変更ディレクティブを指定して構築する。 多文字区切りや実行中の区切り変更（例: MySQL の {@code DELIMITER}）に対応する。
     *
     * @param region スキップする領域（クォート・コメント等）を消費するパーサー
     * @param delimiter ステートメントの初期区切り文字列
     * @param directive 区切り変更指示の検出器（不要なら {@code null}）
     */
    public StatementSplitter(
            SqlParser region, String delimiter, @Nullable DelimiterDirective directive) {
        this.region = region;
        this.delimiter = delimiter;
        this.directive = directive;
    }

    /** 標準的な引用・コメント領域を認識し、{@code ';'} を区切りとするインスタンスを返す。 */
    public static StatementSplitter standard() {
        return new StatementSplitter(SqlParsers.standardRegion(), ';');
    }

    /**
     * SQL テキストを区切り文字で分割し、各ステートメントを外側のみ trim して返す。 trim 後に空になったセグメントは除外する。区切り文字自体は結果に含まれない。
     *
     * <p>先頭のコメント（トリビア）はスキップせず、各セグメントを生のまま保持する。よって先頭の行/ブロックコメントは 後続の文に付随したまま残る（{@code --}
     * 行コメントの改行は文内部に残るため後続文がコメント化されない）。 区切り変更ディレクティブが指定された場合、セグメント先頭（先頭の空白のみスキップ後）で検出された指示は出力に含めず、
     * 以降の区切り文字を切り替える。
     */
    public List<String> split(String sql) {
        List<String> result = new ArrayList<>();
        int len = sql.length();
        String delim = delimiter;
        int start = 0;
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
                Directive d = applyDirective(sql, pos + delim.length(), delim);
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

    /**
     * {@code pos} で区切り変更ディレクティブを連続適用し、確定した区切り文字と次セグメント先頭位置を返す。
     * 検出のための位置探索では先頭の空白のみをスキップし、コメントはスキップしない。
     */
    private Directive applyDirective(String sql, int pos, String delim) {
        if (directive == null) {
            return new Directive(delim, pos);
        }
        String currentDelim = delim;
        int cur = pos;
        while (true) {
            int probe = skipWhitespace(sql, cur);
            DelimiterDirective.@Nullable Result r = directive.detect(sql, probe);
            if (r == null) {
                return new Directive(currentDelim, cur);
            }
            currentDelim = r.newDelimiter();
            cur = r.nextPos();
        }
    }

    /** {@code pos} から空白（{@link Character#isWhitespace}）の連続を読み飛ばした位置を返す。 */
    private int skipWhitespace(String sql, int pos) {
        int i = pos;
        int len = sql.length();
        while (i < len && Character.isWhitespace(sql.charAt(i))) {
            i++;
        }
        return i;
    }

    /** 区切り変更ディレクティブ適用後の区切り文字と位置を保持する内部レコード。 */
    private record Directive(String delimiter, int pos) {}
}
