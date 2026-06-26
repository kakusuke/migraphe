package io.github.kakusuke.migraphe.jdbc.statement;

/**
 * A single parser combinator that attempts to consume a region of SQL text starting at a given
 * position.
 *
 * <p>This is the atomic building block of the parser-combinator toolkit used to split SQL into
 * individual statements while respecting quotes, comments, and dialect-specific regions. A {@code
 * SqlParser} inspects {@code sql} beginning at {@code pos} and reports how far (if at all) it
 * matched: a non-negative return value is the index immediately after the consumed text, while
 * {@code -1} signals no match. Parsers never mutate the input; they only compute positions, which
 * makes them freely composable and reusable.
 *
 * <p>Larger parsers are assembled from smaller ones through the factory methods in {@link
 * SqlParsers} (for example {@link SqlParsers#seq}, {@link SqlParsers#or}, {@link SqlParsers#many}).
 * The composed result is consumed by {@link StatementSplitter}, which repeatedly applies a region
 * parser to skip over quoted strings and comments so that delimiters appearing inside them are not
 * mistaken for statement boundaries. Implementations are typically stateless lambdas and are
 * expected to be safe to reuse across calls.
 */
@FunctionalInterface
public interface SqlParser {

    /**
     * Attempts to parse {@code sql} starting at {@code pos}.
     *
     * @param sql the full SQL text being scanned
     * @param pos the index at which to begin matching
     * @return the index immediately after the consumed text on a successful match, or {@code -1} if
     *     the parser does not match at {@code pos}
     */
    int parse(String sql, int pos);
}
