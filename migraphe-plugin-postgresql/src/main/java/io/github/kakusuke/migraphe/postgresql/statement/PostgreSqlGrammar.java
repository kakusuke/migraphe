package io.github.kakusuke.migraphe.postgresql.statement;

import io.github.kakusuke.migraphe.jdbc.statement.SqlParser;
import io.github.kakusuke.migraphe.jdbc.statement.SqlParsers;
import io.github.kakusuke.migraphe.jdbc.statement.StatementSplitter;

/**
 * Factory for the PostgreSQL-dialect SQL splitting grammar.
 *
 * <p>Builds the {@link SqlParser} and {@link StatementSplitter} used by {@link
 * io.github.kakusuke.migraphe.postgresql.PostgreSQLEnvironment} to split SQL scripts into
 * individual statements. The distinguishing feature versus the generic JDBC grammar is recognition
 * of PostgreSQL dollar-quoted bodies ({@code $tag$ ... $tag$}), which may contain semicolons and
 * newlines without splitting the statement. This is a stateless utility class and cannot be
 * instantiated.
 */
public final class PostgreSqlGrammar {

    private PostgreSqlGrammar() {}

    /**
     * Returns a parser that consumes a PostgreSQL dollar-quoted region {@code $tag$ ... $tag$} as a
     * single unit.
     *
     * <p>It matches only when the start position is a {@code $} and the text between two {@code $}
     * characters forms a valid tag ({@code [A-Za-z_][A-Za-z0-9_]*} or the empty tag {@code $$}). It
     * then consumes everything — including arbitrary characters, newlines, and {@code ;} — up to
     * the first occurrence of an identical closing tag, returning the position immediately after
     * that closing tag. If no closing tag is found, it returns {@code sql.length()} (an
     * unterminated body is treated as one region running to the end of input).
     *
     * <p>A numeric placeholder such as {@code $1} is not a dollar tag, so the parser returns {@code
     * -1} to defer to other parsers.
     *
     * @return a dollar-quote {@link SqlParser}
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

    /**
     * Returns the end of the dollar tag starting at the {@code '$'} at {@code pos} (the position
     * just after the closing {@code '$'}), or {@code -1} if it cannot be read as a dollar tag.
     */
    private static int readTag(String sql, int pos) {
        int len = sql.length();
        // The caller guarantees that the character at pos is '$'.
        int i = pos + 1;
        // Allow the empty tag $$.
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
     * Returns the PostgreSQL-dialect {@link StatementSplitter}.
     *
     * <p>Its protected regions are dollar-quoted bodies plus the standard string/comment regions
     * (from {@link SqlParsers#standardRegion()}), and the statement delimiter is {@code ';'}.
     * Because procedure bodies always live inside a dollar-quoted or string region, no
     * keyword-block or {@code DELIMITER} grammar is needed (unlike the MySQL dialect). A leading
     * comment is kept attached to the statement that follows it.
     *
     * @return the PostgreSQL statement splitter
     */
    public static StatementSplitter splitter() {
        SqlParser region = SqlParsers.or(dollarQuoted(), SqlParsers.standardRegion());
        return new StatementSplitter(region, ";", null);
    }
}
