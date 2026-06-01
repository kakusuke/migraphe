package io.github.kakusuke.migraphe.mysql.statement;

import io.github.kakusuke.migraphe.jdbc.statement.DelimiterDirective;
import io.github.kakusuke.migraphe.jdbc.statement.SqlParser;
import io.github.kakusuke.migraphe.jdbc.statement.SqlParsers;
import io.github.kakusuke.migraphe.jdbc.statement.StatementSplitter;

/** MySQL 方言の SQL 分割文法を提供するファクトリー。 */
public final class MySqlGrammar {

    private MySqlGrammar() {}

    /** バッククォート識別子・各種クォート文字列・コメントを 1 領域として消費するパーサー。 */
    private static SqlParser quoteOrComment() {
        SqlParser singleQuote = SqlParsers.quoted('\'', true, true);
        SqlParser doubleQuote = SqlParsers.quoted('"', true, true);
        SqlParser backtick = SqlParsers.quoted('`', true, false);
        SqlParser dashComment = SqlParsers.lineComment("--", true);
        SqlParser hashComment = SqlParsers.lineComment("#", false);
        SqlParser blockComment = SqlParsers.delimited("/*", "*/");
        return SqlParsers.or(
                singleQuote, doubleQuote, backtick, dashComment, hashComment, blockComment);
    }

    /**
     * MySQL のストアドルーチン本体に現れる複合文ブロック （{@code BEGIN...END}, {@code IF...END IF}, {@code CASE...END
     * [CASE]}, {@code LOOP...END LOOP}, {@code WHILE...END WHILE}, {@code REPEAT...END REPEAT}）を 1
     * 領域として消費するパーサーを返す。
     *
     * <p>内部の文区切り {@code ;} や入れ子ブロックを正しく取り込むため相互再帰で構成する。 {@code content}
     * は「クォート/コメント領域」「入れ子ブロック」「{@code END} 手前の任意 1 文字」のいずれかを 消費し、{@code many(content)} は最初の {@code
     * END} の手前で停止する。各ブロックが {@code END}（+ 固有の後続語） を消費することで、外側ブロックが内側の {@code END} を取り違えることはない。
     *
     * @return 複合文ブロックパーサー
     */
    static SqlParser block() {
        SqlParser quoteOrComment = quoteOrComment();
        // content / block の相互再帰。block を遅延参照で解決する。
        SqlParser[] holder = new SqlParser[1];
        SqlParser content =
                SqlParsers.or(
                        quoteOrComment,
                        SqlParsers.ref(() -> holder[0]),
                        SqlParsers.seq(
                                SqlParsers.not(SqlParsers.keyword("END")), SqlParsers.anyChar()));
        SqlParser body = SqlParsers.many(content);
        // END とその後続キーワードの間には空白が入るため、空白を読み飛ばしてから後続語を照合する。
        SqlParser ws = SqlParsers.many(SqlParsers.whitespace());
        SqlParser beginBlock =
                SqlParsers.seq(SqlParsers.keyword("BEGIN"), body, SqlParsers.keyword("END"));
        SqlParser ifBlock =
                SqlParsers.seq(
                        SqlParsers.keyword("IF"),
                        body,
                        SqlParsers.keyword("END"),
                        ws,
                        SqlParsers.keyword("IF"));
        SqlParser caseBlock =
                SqlParsers.seq(
                        SqlParsers.keyword("CASE"),
                        body,
                        SqlParsers.keyword("END"),
                        SqlParsers.opt(SqlParsers.seq(ws, SqlParsers.keyword("CASE"))));
        SqlParser loopBlock =
                SqlParsers.seq(
                        SqlParsers.keyword("LOOP"),
                        body,
                        SqlParsers.keyword("END"),
                        ws,
                        SqlParsers.keyword("LOOP"));
        SqlParser whileBlock =
                SqlParsers.seq(
                        SqlParsers.keyword("WHILE"),
                        body,
                        SqlParsers.keyword("END"),
                        ws,
                        SqlParsers.keyword("WHILE"));
        SqlParser repeatBlock =
                SqlParsers.seq(
                        SqlParsers.keyword("REPEAT"),
                        body,
                        SqlParsers.keyword("END"),
                        ws,
                        SqlParsers.keyword("REPEAT"));
        SqlParser block =
                SqlParsers.or(beginBlock, ifBlock, caseBlock, loopBlock, whileBlock, repeatBlock);
        holder[0] = block;
        return block;
    }

    /**
     * 行頭の {@code DELIMITER} ディレクティブを検出する {@link DelimiterDirective} を返す。
     *
     * <p>大文字小文字を無視した {@code DELIMITER} の直後に 1 つ以上の空白（改行を除く）が続き、 その後に行末（{@code \r} / {@code \n} /
     * EOF）までの非空白連続を新しい区切りトークンとして読み取る。 検出時は指示行自体を出力させないため、行末の改行直後（または EOF）を次位置として返す。非検出は {@code
     * null}。
     *
     * @return DELIMITER ディレクティブ検出器
     */
    static DelimiterDirective delimiterDirective() {
        return (sql, pos) -> {
            String keyword = "DELIMITER";
            int len = sql.length();
            if (!sql.regionMatches(true, pos, keyword, 0, keyword.length())) {
                return null;
            }
            // 語境界: 直前が識別子文字でないこと。
            if (pos > 0 && isIdentifierChar(sql.charAt(pos - 1))) {
                return null;
            }
            int i = pos + keyword.length();
            // 直後に 1 つ以上の水平空白を要求する。
            int spaceStart = i;
            while (i < len && isHorizontalSpace(sql.charAt(i))) {
                i++;
            }
            if (i == spaceStart) {
                return null;
            }
            // 行末までの非空白連続を区切りトークンとして読む。
            int tokenStart = i;
            while (i < len && !isLineEnd(sql.charAt(i))) {
                i++;
            }
            String token = sql.substring(tokenStart, i).trim();
            if (token.isEmpty()) {
                return null;
            }
            // 改行直後（または EOF）まで進める。
            int nextPos = i;
            if (nextPos < len) {
                char c = sql.charAt(nextPos);
                if (c == '\r' && nextPos + 1 < len && sql.charAt(nextPos + 1) == '\n') {
                    nextPos += 2;
                } else {
                    nextPos += 1;
                }
            }
            return new DelimiterDirective.Result(token, nextPos);
        };
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean isHorizontalSpace(char c) {
        return c == ' ' || c == '\t';
    }

    private static boolean isLineEnd(char c) {
        return c == '\n' || c == '\r';
    }

    /**
     * MySQL 方言の {@link StatementSplitter} を返す。
     *
     * <p>領域はクォート/コメントと複合文ブロック、初期区切り文字は {@code ';'}。{@code DELIMITER} ディレクティブで実行中に区切りを変更できる。
     * 先頭コメントは後続文に付随して保持される。
     *
     * @return MySQL 用ステートメント分割器
     */
    public static StatementSplitter splitter() {
        SqlParser region = SqlParsers.or(quoteOrComment(), block());
        return new StatementSplitter(region, ";", delimiterDirective());
    }
}
