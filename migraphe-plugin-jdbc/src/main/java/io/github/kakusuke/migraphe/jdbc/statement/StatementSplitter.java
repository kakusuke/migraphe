package io.github.kakusuke.migraphe.jdbc.statement;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Splits a body of SQL text into individual statements at a delimiter, ignoring delimiters that
 * fall inside quotes, comments, or other dialect-specific regions.
 *
 * <p>This is the driver of Migraphe's parser-combinator statement-splitting toolkit. It is
 * configured with a <em>region parser</em> — a {@link SqlParser} built from the combinators in
 * {@link SqlParsers} — that recognizes spans (string literals, quoted identifiers, line and block
 * comments, and dialect features such as PostgreSQL dollar-quoting or MySQL {@code BEGIN}/{@code
 * END} blocks) within which the delimiter must not be treated as a statement boundary. The splitter
 * scans character by character: at each position it first tries the region parser and, on a match,
 * jumps past the entire region; otherwise it checks for the delimiter and, when found, emits the
 * accumulated text as one statement.
 *
 * <p>Optionally a {@link DelimiterDirective} may be supplied to support in-stream delimiter changes
 * (for example MySQL's {@code DELIMITER} command), allowing the active delimiter to switch between
 * statements. Construct the common case via {@link #standard()}; dialect plugins build their own
 * instances with a richer region parser and, where needed, a delimiter directive.
 */
public final class StatementSplitter {

    private final SqlParser region;
    private final String delimiter;
    private final @Nullable DelimiterDirective directive;

    /**
     * Constructs a splitter with a single-character delimiter and no delimiter directive.
     *
     * <p>Use this when a dialect contributes custom regions (for example PostgreSQL dollar-quoting)
     * but keeps a fixed single-character delimiter.
     *
     * @param region the parser that consumes regions (quotes, comments, etc.) to skip over
     * @param delimiter the character that separates statements
     */
    public StatementSplitter(SqlParser region, char delimiter) {
        this(region, String.valueOf(delimiter), null);
    }

    /**
     * Constructs a splitter with a string delimiter and an optional delimiter-change directive.
     *
     * <p>This form supports multi-character delimiters and runtime delimiter changes (for example
     * MySQL's {@code DELIMITER} command, detected via {@code directive}).
     *
     * @param region the parser that consumes regions (quotes, comments, etc.) to skip over
     * @param delimiter the initial string used to separate statements
     * @param directive the detector for in-stream delimiter changes, or {@code null} if not needed
     */
    public StatementSplitter(
            SqlParser region, String delimiter, @Nullable DelimiterDirective directive) {
        this.region = region;
        this.delimiter = delimiter;
        this.directive = directive;
    }

    /**
     * Creates a splitter recognizing the standard SQL regions and using {@code ';'} as the
     * delimiter.
     *
     * <p>The region parser is {@link SqlParsers#standardRegion()}, which covers single-quoted
     * strings, double-quoted identifiers, {@code --} line comments, and {@code /*}...{@code
     * *}{@code /} block comments. No delimiter directive is configured.
     *
     * @return a splitter for plain semicolon-delimited SQL with standard quote and comment handling
     */
    public static StatementSplitter standard() {
        return new StatementSplitter(SqlParsers.standardRegion(), ';');
    }

    /**
     * Splits the given SQL text into statements at the configured delimiter.
     *
     * <p>Each emitted statement is trimmed at its outer edges; segments that are empty after
     * trimming are omitted, and the delimiter itself is never included in the output. Regions
     * recognized by the region parser are skipped wholesale, so delimiters inside strings,
     * comments, or dialect blocks are not treated as boundaries.
     *
     * <p>Leading trivia is not stripped: each segment is kept verbatim, so a leading line or block
     * comment stays attached to the statement that follows it (and because a {@code --} line
     * comment's terminating newline is retained inside the segment, the following statement is not
     * accidentally commented out). When a {@link DelimiterDirective} is configured, a directive
     * detected at the start of a segment (after skipping only leading whitespace) is consumed
     * without being emitted, and the active delimiter is switched for subsequent statements.
     *
     * @param sql the SQL text to split
     * @return a list of trimmed, non-empty statements in source order
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
     * Repeatedly applies the delimiter directive at {@code pos}, returning the resolved delimiter
     * and the position at which the next segment begins.
     *
     * <p>If no directive is configured, the input delimiter and position are returned unchanged.
     * When probing for a directive, only leading whitespace is skipped — comments are not skipped —
     * and detection loops so that consecutive directives are all consumed.
     *
     * @param sql the SQL text being split
     * @param pos the position at which the next segment begins
     * @param delim the currently active delimiter
     * @return the resolved delimiter and the next segment's start position
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

    /**
     * Returns the position after skipping a run of whitespace starting at {@code pos}.
     *
     * @param sql the SQL text being scanned
     * @param pos the position at which to start skipping
     * @return the index of the first non-{@linkplain Character#isWhitespace whitespace} character
     *     at or after {@code pos}, or {@code sql.length()} if only whitespace remains
     */
    private int skipWhitespace(String sql, int pos) {
        int i = pos;
        int len = sql.length();
        while (i < len && Character.isWhitespace(sql.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * Internal carrier for the delimiter and segment-start position after applying delimiter
     * directives.
     *
     * @param delimiter the active delimiter after directive resolution
     * @param pos the position at which the next segment begins
     */
    private record Directive(String delimiter, int pos) {}
}
