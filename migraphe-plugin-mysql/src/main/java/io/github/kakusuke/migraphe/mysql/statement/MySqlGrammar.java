package io.github.kakusuke.migraphe.mysql.statement;

import io.github.kakusuke.migraphe.jdbc.statement.DelimiterDirective;
import io.github.kakusuke.migraphe.jdbc.statement.SqlParser;
import io.github.kakusuke.migraphe.jdbc.statement.SqlParsers;
import io.github.kakusuke.migraphe.jdbc.statement.StatementSplitter;

/**
 * Factory for the MySQL-dialect SQL splitting grammar.
 *
 * <p>Assembles the parser combinators ({@link SqlParser} / {@link SqlParsers}) that make {@link
 * StatementSplitter} aware of MySQL syntax — backtick-quoted identifiers, {@code '} and {@code "}
 * string literals, line comments ({@code --} and {@code #}) and C-style block comments, recursive
 * compound-statement blocks, and the {@code DELIMITER} directive — so that statement delimiters
 * appearing inside those regions are not treated as boundaries. The assembled splitter is exposed
 * through {@link #splitter()} and wired into the MySQL environment's statement splitter.
 *
 * <p>This class is not instantiable.
 *
 * @see StatementSplitter
 */
public final class MySqlGrammar {

    private MySqlGrammar() {}

    /**
     * Returns a parser that consumes, as a single skipped region, a backtick-quoted identifier, a
     * single- or double-quoted string literal, a {@code --} or {@code #} line comment, or a C-style
     * block comment.
     *
     * @return a parser matching any one quote-or-comment region
     */
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
     * Returns a parser that consumes, as a single region, a MySQL compound-statement block found in
     * stored-routine bodies: {@code BEGIN...END}, {@code IF...END IF}, {@code CASE...END [CASE]},
     * {@code LOOP...END LOOP}, {@code WHILE...END WHILE}, and {@code REPEAT...END REPEAT}.
     *
     * <p>The parser is built with mutual recursion so that inner statement delimiters ({@code ;})
     * and nested blocks are absorbed correctly. The {@code content} parser matches a quote/comment
     * region, a nested block, or any single character that is not the start of an {@code END}
     * keyword; {@code many(content)} therefore stops just before the first {@code END}. Because
     * each block consumes its own {@code END} (plus any block-specific trailing keyword), an outer
     * block never mistakes an inner block's {@code END} for its own.
     *
     * <p>A keyword that opens no block — the {@code IF} of {@code DROP TABLE IF EXISTS}, say — is
     * rejected only after the body has scanned ahead for a matching {@code END} that never arrives.
     * Because the body admits nested blocks, that failing scan would be repeated for every such
     * keyword it passes over, costing O(2<sup>k</sup>) for k of them in one script. The assembled
     * block parser is therefore {@linkplain SqlParsers#memoize(SqlParser) memoized}, which computes
     * each position once without changing which spans are recognized.
     *
     * @return a parser matching one compound-statement block
     */
    static SqlParser block() {
        SqlParser quoteOrComment = quoteOrComment();
        // Mutual recursion between content and block; resolve block via a lazy reference.
        SqlParser[] holder = new SqlParser[1];
        SqlParser content =
                SqlParsers.or(
                        quoteOrComment,
                        SqlParsers.ref(() -> holder[0]),
                        SqlParsers.seq(
                                SqlParsers.not(SqlParsers.keyword("END")), SqlParsers.anyChar()));
        SqlParser body = SqlParsers.many(content);
        // Whitespace may appear between END and its trailing keyword, so skip it before matching.
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
        // Memoized so a candidate rejected at a position is never re-derived; see the Javadoc.
        SqlParser block =
                SqlParsers.memoize(
                        SqlParsers.or(
                                beginBlock,
                                ifBlock,
                                caseBlock,
                                loopBlock,
                                whileBlock,
                                repeatBlock));
        holder[0] = block;
        return block;
    }

    /**
     * Returns a {@link DelimiterDirective} that detects a {@code DELIMITER} directive at the start
     * of a line.
     *
     * <p>It matches {@code DELIMITER} case-insensitively at a word boundary, requires one or more
     * horizontal spaces (no newlines) immediately after it, and then reads the following run of
     * non-whitespace characters up to the end of line ({@code \r} / {@code \n} / EOF) as the new
     * delimiter token. On a match it advances the next position to just after the end-of-line
     * newline (or to EOF) so that the directive line itself is not emitted. It returns {@code null}
     * when no directive is present.
     *
     * @return a detector for the {@code DELIMITER} directive
     */
    static DelimiterDirective delimiterDirective() {
        return (sql, pos) -> {
            String keyword = "DELIMITER";
            int len = sql.length();
            if (!sql.regionMatches(true, pos, keyword, 0, keyword.length())) {
                return null;
            }
            // Word boundary: the preceding character must not be an identifier character.
            if (pos > 0 && isIdentifierChar(sql.charAt(pos - 1))) {
                return null;
            }
            int i = pos + keyword.length();
            // Require one or more horizontal spaces immediately after the keyword.
            int spaceStart = i;
            while (i < len && isHorizontalSpace(sql.charAt(i))) {
                i++;
            }
            if (i == spaceStart) {
                return null;
            }
            // Read the run of non-whitespace up to end of line as the delimiter token.
            int tokenStart = i;
            while (i < len && !isLineEnd(sql.charAt(i))) {
                i++;
            }
            String token = sql.substring(tokenStart, i).trim();
            if (token.isEmpty()) {
                return null;
            }
            // Advance past the end-of-line newline (or to EOF).
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
     * Returns a {@link StatementSplitter} configured for the MySQL dialect.
     *
     * <p>Its skipped regions are quote/comment regions and compound-statement blocks, its initial
     * delimiter is {@code ';'}, and it honors the {@code DELIMITER} directive to change the
     * delimiter mid-stream. Leading comments are retained with the statement that follows them.
     *
     * @return a MySQL-dialect statement splitter
     */
    public static StatementSplitter splitter() {
        SqlParser region = SqlParsers.or(quoteOrComment(), block());
        return new StatementSplitter(region, ";", delimiterDirective());
    }
}
