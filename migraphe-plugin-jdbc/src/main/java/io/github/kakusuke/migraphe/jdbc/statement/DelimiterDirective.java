package io.github.kakusuke.migraphe.jdbc.statement;

import org.jspecify.annotations.Nullable;

/**
 * Detects an in-stream change of the statement delimiter at the start of a segment (for example
 * MySQL's {@code DELIMITER} command).
 *
 * <p>Some SQL dialects let a script reassign the delimiter mid-stream so that compound statements
 * containing semicolons (such as stored routines bracketed by {@code BEGIN}/{@code END}) can be
 * sent as a single statement. {@link StatementSplitter} consults a {@code DelimiterDirective} at
 * the beginning of each segment — after skipping only leading whitespace — and, when one is
 * detected, omits the directive text itself from the output and switches the active delimiter to
 * {@link Result#newDelimiter()} for subsequent statements.
 *
 * <p>Implementations are typically supplied by dialect grammars (for example the MySQL plugin) and
 * passed to {@link StatementSplitter#StatementSplitter(SqlParser, String, DelimiterDirective)}.
 */
public interface DelimiterDirective {

    /**
     * Attempts to detect a delimiter-change directive starting at {@code pos}.
     *
     * @param sql the SQL text being scanned
     * @param pos the position at which to attempt detection (the start of a segment, after leading
     *     whitespace has been skipped)
     * @return a {@link Result} carrying the new delimiter and the position just past the consumed
     *     directive, or {@code null} if no directive is present at {@code pos}
     */
    @Nullable Result detect(String sql, int pos);

    /**
     * The outcome of a successful delimiter-change detection.
     *
     * @param newDelimiter the delimiter to use for subsequent statements
     * @param nextPos the position immediately after the consumed directive
     */
    record Result(String newDelimiter, int nextPos) {}
}
