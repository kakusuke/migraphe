package io.github.kakusuke.migraphe.jdbc.statement;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Factory methods for building and composing {@link SqlParser} combinators.
 *
 * <p>This class is the combinator library at the heart of Migraphe's SQL statement-splitting
 * toolkit. It provides two kinds of factories: <em>primitive recognizers</em> that match concrete
 * lexical features ({@link #whitespace()}, {@link #literal(String)}, {@link #keyword(String)},
 * {@link #quoted(char, boolean, boolean)}, {@link #lineComment(String, boolean)}, {@link
 * #delimited(String, String)}, {@link #anyChar()}), and <em>combinators</em> that compose other
 * parsers into larger ones ({@link #seq(SqlParser...)}, {@link #or(SqlParser...)}, {@link
 * #many(SqlParser)}, {@link #opt(SqlParser)}, {@link #not(SqlParser)}, {@link #ref}, {@link
 * #memoize(SqlParser)}).
 *
 * <p>All parsers follow the same contract as {@link SqlParser#parse(String, int)}: a non-negative
 * return value is the position after the consumed text, and {@code -1} means no match. Because
 * parsers are pure position-computing functions, dialect grammars (PostgreSQL dollar-quoting, MySQL
 * backtick identifiers and {@code BEGIN}/{@code END} blocks, etc.) are expressed simply by
 * combining these factories, then handed to {@link StatementSplitter} as the region parser that
 * marks the spans a statement delimiter must not be recognized inside. The class is a stateless
 * utility and cannot be instantiated.
 */
public final class SqlParsers {

    private SqlParsers() {}

    /**
     * Creates a parser that consumes a single whitespace character.
     *
     * @return a parser that returns {@code pos + 1} when the character at {@code pos} is
     *     {@linkplain Character#isWhitespace whitespace}, or {@code -1} otherwise (including at end
     *     of input)
     */
    public static SqlParser whitespace() {
        return (sql, pos) ->
                pos < sql.length() && Character.isWhitespace(sql.charAt(pos)) ? pos + 1 : -1;
    }

    /**
     * Creates a parser that matches an exact, case-sensitive substring.
     *
     * @param token the literal text to match starting at {@code pos}
     * @return a parser that returns {@code pos + token.length()} when {@code token} occurs exactly
     *     at {@code pos}, or {@code -1} otherwise
     */
    public static SqlParser literal(String token) {
        return (sql, pos) -> sql.startsWith(token, pos) ? pos + token.length() : -1;
    }

    /**
     * Creates a parser that applies the given parsers in order, requiring all of them to succeed.
     *
     * <p>Each parser is fed the position produced by its predecessor. If any parser returns {@code
     * -1}, the sequence fails immediately and consumes nothing.
     *
     * @param parsers the sub-parsers to apply consecutively
     * @return a parser that returns the final position when every sub-parser matches, or {@code -1}
     *     as soon as one fails
     */
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

    /**
     * Creates an ordered-choice parser that returns the result of the first sub-parser to succeed.
     *
     * <p>Each sub-parser is tried at the same {@code pos}; the first one returning a non-negative
     * position wins. If none match, the result is {@code -1}.
     *
     * @param parsers the alternative sub-parsers to try, in priority order
     * @return a parser yielding the first successful sub-parser's position, or {@code -1} if all
     *     fail
     */
    public static SqlParser or(SqlParser... parsers) {
        return (sql, pos) -> {
            for (SqlParser parser : parsers) {
                int p = parser.parse(sql, pos);
                if (p >= 0) return p;
            }
            return -1;
        };
    }

    /**
     * Creates a parser that consumes exactly one character if any remains.
     *
     * @return a parser that returns {@code pos + 1} when {@code pos} is within bounds, or {@code
     *     -1} at end of input
     */
    public static SqlParser anyChar() {
        return (sql, pos) -> pos < sql.length() ? pos + 1 : -1;
    }

    /**
     * Creates a negative-lookahead parser that succeeds without consuming input when the given
     * parser fails.
     *
     * @param p the parser whose failure is required
     * @return a parser that returns {@code pos} (consuming nothing) when {@code p} fails at {@code
     *     pos}, or {@code -1} when {@code p} succeeds
     */
    public static SqlParser not(SqlParser p) {
        return (sql, pos) -> p.parse(sql, pos) < 0 ? pos : -1;
    }

    /**
     * Creates a greedy zero-or-more repetition parser.
     *
     * <p>The given parser is applied repeatedly and the farthest reached position is returned. The
     * combinator always succeeds (matching zero repetitions yields {@code pos}). To avoid an
     * infinite loop, repetition stops if {@code p} succeeds without advancing the position.
     *
     * @param p the parser to repeat
     * @return a parser that returns the farthest position reachable by repeating {@code p}
     */
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

    /**
     * Creates an optional parser that never fails.
     *
     * @param p the parser to attempt
     * @return a parser that returns {@code p}'s position when it matches, or {@code pos} (consuming
     *     nothing) when it does not
     */
    public static SqlParser opt(SqlParser p) {
        return (sql, pos) -> {
            int next = p.parse(sql, pos);
            return next < 0 ? pos : next;
        };
    }

    /**
     * Creates a parser that matches a keyword case-insensitively, honoring identifier word
     * boundaries.
     *
     * <p>The match succeeds only when {@code word} appears at {@code pos} (ignoring case) and is
     * not adjacent to identifier characters on either side: the character before {@code pos} (when
     * {@code pos > 0}) and the character immediately after the match must each not be an identifier
     * character (a {@linkplain Character#isLetterOrDigit letter or digit} or {@code '_'}). This
     * prevents, for example, {@code "BEGIN"} from matching inside {@code "BEGINNING"}.
     *
     * @param word the keyword to match
     * @return a parser that returns the position after the keyword on a bounded, case-insensitive
     *     match, or {@code -1} otherwise
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

    /**
     * Creates a lazily resolved parser that delegates to a parser obtained from the supplier on
     * each call.
     *
     * <p>This is used to break forward references when defining mutually recursive grammars (for
     * example a block parser that contains nested blocks of the same kind).
     *
     * @param supplier the supplier consulted at parse time to obtain the delegate parser
     * @return a parser that forwards each {@code parse} call to {@code supplier.get()}
     */
    public static SqlParser ref(java.util.function.Supplier<SqlParser> supplier) {
        return (sql, pos) -> supplier.get().parse(sql, pos);
    }

    /**
     * Marker stored in a memo table for a position whose result has not been computed yet.
     *
     * <p>{@link SqlParser#parse(String, int)} returns either {@code -1} (no match) or a position in
     * {@code [0, sql.length()]}, so {@code -2} can never be a real result and a table pre-filled
     * with it can store results verbatim. Widening that return contract would break this
     * assumption.
     */
    private static final int UNCOMPUTED = -2;

    /**
     * Creates a parser that caches the delegate's result per input position (packrat memoization).
     *
     * <p>Wrapping a recursive parser in {@code memoize} turns repeated failed attempts at the same
     * position from exponential into linear work: without it, a grammar whose block body may itself
     * contain blocks re-scans the remainder of the input once per nested candidate, so a script
     * with <em>k</em> block-opening keywords that never close (for example the {@code IF} in {@code
     * DROP TABLE IF EXISTS}) costs O(2<sup>k</sup>). The cached decisions are identical to the
     * uncached ones, so memoizing changes only cost, never which spans a grammar recognizes.
     *
     * <p>Requirements and caveats:
     *
     * <ul>
     *   <li>The delegate must be a pure function of the input and position it is given — the same
     *       arguments must always yield the same result. Every combinator in this class satisfies
     *       this.
     *   <li>One memo table is held at a time and is discarded as soon as the returned parser is
     *       handed a different (non-{@linkplain String#equals equal}) input, so a memoized parser
     *       is reusable across inputs without retaining them.
     *   <li>The table is not synchronized. Sharing one memoized parser across threads stays correct
     *       — because the delegate is pure, a thread that misses the cache merely recomputes a
     *       value another thread already holds — but concurrent users may duplicate work.
     * </ul>
     *
     * @param parser the parser whose results should be cached; must be a pure function of the input
     *     and position it is given
     * @return a parser that returns the same results as {@code parser} while computing each
     *     position at most once per input
     */
    public static SqlParser memoize(SqlParser parser) {
        return new MemoizingParser(parser);
    }

    /**
     * A {@link SqlParser} that caches its delegate's result for each position of the most recent
     * input.
     *
     * <p>The table is published without synchronization. That is safe because {@link Memo}'s fields
     * are final — a racing reader therefore cannot observe a half-built table — and because the
     * delegate is required to be pure, which makes a missed cache entry cost at most a
     * recomputation of the same value.
     */
    private static final class MemoizingParser implements SqlParser {

        private final SqlParser delegate;
        private @Nullable Memo memo;

        MemoizingParser(SqlParser delegate) {
            this.delegate = delegate;
        }

        @Override
        public int parse(String sql, int pos) {
            @Nullable Memo current = memo;
            if (current == null || !current.sql.equals(sql)) {
                current = new Memo(sql);
                memo = current;
            }
            int cached = current.results[pos];
            if (cached != UNCOMPUTED) {
                return cached;
            }
            int end = delegate.parse(sql, pos);
            current.results[pos] = end;
            return end;
        }
    }

    /**
     * Memo table for a single input: {@code results[pos]} holds the parse result at {@code pos}, or
     * {@link #UNCOMPUTED} while it is still unknown.
     *
     * <p>The table is filled inside the constructor deliberately. {@link MemoizingParser} publishes
     * a {@code Memo} without synchronization and leans on the final-field freeze, which covers the
     * array contents only as they stood when the constructor returned. Filling after publication
     * would let a racing reader see a default {@code 0} and mistake it for a cached "matched at
     * position 0, consumed nothing".
     */
    private static final class Memo {

        private final String sql;
        private final int[] results;

        Memo(String sql) {
            this.sql = sql;
            this.results = new int[sql.length() + 1];
            Arrays.fill(this.results, UNCOMPUTED);
        }
    }

    /**
     * Creates a parser that consumes a quoted region delimited by the given quote character.
     *
     * <p>The region must begin with {@code quote} at {@code pos}, otherwise the parser returns
     * {@code -1}. While scanning for the closing quote: when {@code doubling} is {@code true}, a
     * doubled quote ({@code quote} immediately followed by {@code quote}) is treated as an escaped
     * quote and skipped as two characters; when {@code backslashEscape} is {@code true}, a
     * backslash ({@code '\'}) causes the following character to be skipped as part of an escape
     * sequence. On a successful close, the position after the closing quote is returned. If no
     * closing quote is found before the end of input, the unterminated region is treated as
     * extending to the end and {@code sql.length()} is returned.
     *
     * @param quote the character that opens and closes the region (for example {@code '\''} for a
     *     string literal or {@code '"'} for a quoted identifier)
     * @param doubling whether a doubled quote escapes a literal quote inside the region
     * @param backslashEscape whether a backslash escapes the next character inside the region
     * @return a parser that returns the position after the region, or {@code -1} if it does not
     *     start with {@code quote}
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
     * Creates a parser that consumes a line comment introduced by the given prefix.
     *
     * <p>The comment must begin with {@code prefix} at {@code pos}, otherwise the parser returns
     * {@code -1}. When {@code requireSpaceAfter} is {@code true}, the character immediately after
     * the prefix must be {@linkplain Character#isWhitespace whitespace} or the end of input,
     * otherwise the parser fails — this models MySQL's requirement that a {@code "--"} comment be
     * written as {@code "-- "}. On a match, the parser stops just before the next {@code '\n'} (so
     * the newline itself is not consumed) and returns that position, or {@code sql.length()} if no
     * newline follows.
     *
     * @param prefix the text that introduces the line comment (for example {@code "--"} or {@code
     *     "#"})
     * @param requireSpaceAfter whether the prefix must be followed by whitespace or end of input
     * @return a parser that returns the position just before the terminating newline (or end of
     *     input), or {@code -1} if no comment starts at {@code pos}
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
     * Creates a parser that consumes a region bounded by an opening and closing token.
     *
     * <p>The region must begin with {@code open} at {@code pos}, otherwise the parser returns
     * {@code -1}. After consuming {@code open}, the parser searches for the first occurrence of
     * {@code close} and, if found, returns the position after it. If {@code close} is never found,
     * the unterminated region is treated as extending to the end of input and {@code sql.length()}
     * is returned. This is used for block comments (for example {@code "/*"} to {@code "*}{@code
     * /"}).
     *
     * @param open the token that opens the region
     * @param close the token that closes the region
     * @return a parser that returns the position after the closing token (or end of input if
     *     unterminated), or {@code -1} if the region does not start with {@code open}
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
     * Creates a parser recognizing the standard SQL quote and comment regions.
     *
     * <p>This is the default region parser used by {@link StatementSplitter#standard()}. It is an
     * ordered choice over a single-quoted string literal (with doubled-quote escaping), a
     * double-quoted identifier (with doubled-quote escaping), a {@code --} line comment, and a
     * {@code /*}...{@code *}{@code /} block comment. Dialect grammars typically extend this set
     * with additional alternatives (such as PostgreSQL dollar-quoting or MySQL backtick
     * identifiers).
     *
     * @return a parser that consumes a standard string literal, quoted identifier, line comment, or
     *     block comment at {@code pos}, or returns {@code -1} if none apply
     */
    public static SqlParser standardRegion() {
        return or(
                quoted('\'', true, false),
                quoted('"', true, false),
                lineComment("--", false),
                delimited("/*", "*/"));
    }
}
