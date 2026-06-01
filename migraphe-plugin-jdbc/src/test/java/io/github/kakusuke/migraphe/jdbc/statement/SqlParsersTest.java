package io.github.kakusuke.migraphe.jdbc.statement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SqlParsersTest {

    @Test
    void literalMatchesAtPosition() {
        assertThat(SqlParsers.literal("SELECT").parse("SELECT 1", 0)).isEqualTo(6);
        assertThat(SqlParsers.literal("INSERT").parse("SELECT 1", 0)).isEqualTo(-1);
        assertThat(SqlParsers.literal("1").parse("SELECT 1", 7)).isEqualTo(8);
    }

    @Test
    void seqConsumesAllParsersInOrder() {
        SqlParser p =
                SqlParsers.seq(
                        SqlParsers.literal("SELECT"),
                        SqlParsers.literal(" "),
                        SqlParsers.literal("1"));
        assertThat(p.parse("SELECT 1", 0)).isEqualTo(8);
        assertThat(
                        SqlParsers.seq(SqlParsers.literal("SELECT"), SqlParsers.literal("X"))
                                .parse("SELECT 1", 0))
                .isEqualTo(-1);
    }

    @Test
    void orReturnsFirstMatchingParser() {
        SqlParser p = SqlParsers.or(SqlParsers.literal("INSERT"), SqlParsers.literal("SELECT"));
        assertThat(p.parse("SELECT 1", 0)).isEqualTo(6);
    }

    @Test
    void orReturnsFirstParserWhenItMatches() {
        SqlParser p = SqlParsers.or(SqlParsers.literal("SEL"), SqlParsers.literal("SELECT"));
        assertThat(p.parse("SELECT 1", 0)).isEqualTo(3);
    }

    @Test
    void orReturnsMinusOneWhenAllFail() {
        SqlParser p = SqlParsers.or(SqlParsers.literal("INSERT"), SqlParsers.literal("UPDATE"));
        assertThat(p.parse("SELECT 1", 0)).isEqualTo(-1);
    }

    @Test
    void anyCharConsumesOneCharacter() {
        assertThat(SqlParsers.anyChar().parse("ab", 0)).isEqualTo(1);
        assertThat(SqlParsers.anyChar().parse("ab", 1)).isEqualTo(2);
    }

    @Test
    void anyCharFailsAtEnd() {
        assertThat(SqlParsers.anyChar().parse("ab", 2)).isEqualTo(-1);
        assertThat(SqlParsers.anyChar().parse("", 0)).isEqualTo(-1);
    }

    @Test
    void notSucceedsWithoutConsumingWhenInnerFails() {
        SqlParser p = SqlParsers.not(SqlParsers.literal("X"));
        assertThat(p.parse("abc", 0)).isEqualTo(0);
        assertThat(p.parse("abc", 1)).isEqualTo(1);
    }

    @Test
    void notFailsWhenInnerSucceeds() {
        SqlParser p = SqlParsers.not(SqlParsers.literal("a"));
        assertThat(p.parse("abc", 0)).isEqualTo(-1);
    }

    @Test
    void manyRepeatsGreedily() {
        SqlParser p = SqlParsers.many(SqlParsers.literal("ab"));
        assertThat(p.parse("ababab", 0)).isEqualTo(6);
        assertThat(p.parse("abXab", 0)).isEqualTo(2);
    }

    @Test
    void manySucceedsWithZeroRepetitions() {
        SqlParser p = SqlParsers.many(SqlParsers.literal("ab"));
        assertThat(p.parse("xyz", 0)).isEqualTo(0);
    }

    @Test
    void manyStopsWhenInnerDoesNotAdvance() {
        // not() succeeds without consuming -> many must not loop forever
        SqlParser p = SqlParsers.many(SqlParsers.not(SqlParsers.literal("X")));
        assertThat(p.parse("abc", 0)).isEqualTo(0);
    }

    @Test
    void optReturnsPosWhenInnerSucceeds() {
        SqlParser p = SqlParsers.opt(SqlParsers.literal("ab"));
        assertThat(p.parse("abc", 0)).isEqualTo(2);
    }

    @Test
    void optReturnsPosWithoutConsumingWhenInnerFails() {
        SqlParser p = SqlParsers.opt(SqlParsers.literal("ab"));
        assertThat(p.parse("xyz", 0)).isEqualTo(0);
        assertThat(p.parse("xyz", 1)).isEqualTo(1);
    }

    @Test
    void keywordMatchesAtWordBoundary() {
        assertThat(SqlParsers.keyword("END").parse("END IF", 0)).isEqualTo(3);
        assertThat(SqlParsers.keyword("END").parse("END", 0)).isEqualTo(3);
    }

    @Test
    void keywordIsCaseInsensitive() {
        assertThat(SqlParsers.keyword("END").parse("end;", 0)).isEqualTo(3);
        assertThat(SqlParsers.keyword("end").parse("foo end;", 4)).isEqualTo(7);
    }

    @Test
    void keywordFailsWhenFollowedByIdentifierChar() {
        assertThat(SqlParsers.keyword("END").parse("ENDIF", 0)).isEqualTo(-1);
        assertThat(SqlParsers.keyword("END").parse("END_X", 0)).isEqualTo(-1);
        assertThat(SqlParsers.keyword("END").parse("END9", 0)).isEqualTo(-1);
    }

    @Test
    void keywordFailsWhenPrecededByIdentifierChar() {
        assertThat(SqlParsers.keyword("END").parse("xEND IF", 1)).isEqualTo(-1);
        assertThat(SqlParsers.keyword("END").parse("9END", 1)).isEqualTo(-1);
    }

    @Test
    void keywordFailsWhenNoMatch() {
        assertThat(SqlParsers.keyword("END").parse("BEGIN", 0)).isEqualTo(-1);
    }

    @Test
    void refResolvesLazily() {
        java.util.concurrent.atomic.AtomicReference<SqlParser> holder =
                new java.util.concurrent.atomic.AtomicReference<>();
        SqlParser ref = SqlParsers.ref(holder::get);
        // holder is empty until now; assign after ref is created
        holder.set(SqlParsers.literal("ab"));
        assertThat(ref.parse("abc", 0)).isEqualTo(2);
        assertThat(ref.parse("xyz", 0)).isEqualTo(-1);
    }

    @Test
    void quotedConsumesSimpleQuotedRegion() {
        SqlParser p = SqlParsers.quoted('\'', false, false);
        assertThat(p.parse("'abc' rest", 0)).isEqualTo(5);
    }

    @Test
    void quotedFailsWhenNotStartingWithQuote() {
        SqlParser p = SqlParsers.quoted('\'', false, false);
        assertThat(p.parse("abc", 0)).isEqualTo(-1);
    }

    @Test
    void quotedHandlesDoubledQuoteAsEscape() {
        SqlParser p = SqlParsers.quoted('\'', true, false);
        // 'it''s' -> closes after final quote at index 6
        assertThat(p.parse("'it''s'X", 0)).isEqualTo(7);
    }

    @Test
    void quotedHandlesBackslashEscape() {
        SqlParser p = SqlParsers.quoted('\'', false, true);
        // 'a\'b' : backslash escapes the inner quote, closes at the last quote
        assertThat(p.parse("'a\\'b'X", 0)).isEqualTo(6);
    }

    @Test
    void quotedReturnsLengthWhenUnterminated() {
        SqlParser p = SqlParsers.quoted('\'', false, false);
        assertThat(p.parse("'abc", 0)).isEqualTo(4);
    }

    @Test
    void quotedDoubleQuoteIdentifier() {
        SqlParser p = SqlParsers.quoted('"', true, false);
        assertThat(p.parse("\"col\" x", 0)).isEqualTo(5);
    }

    @Test
    void lineCommentConsumesUntilNewline() {
        SqlParser p = SqlParsers.lineComment("--", false);
        assertThat(p.parse("-- comment\nSELECT", 0)).isEqualTo(10);
    }

    @Test
    void lineCommentConsumesToEndWhenNoNewline() {
        SqlParser p = SqlParsers.lineComment("--", false);
        assertThat(p.parse("-- comment", 0)).isEqualTo(10);
    }

    @Test
    void lineCommentFailsWhenPrefixAbsent() {
        SqlParser p = SqlParsers.lineComment("--", false);
        assertThat(p.parse("SELECT", 0)).isEqualTo(-1);
    }

    @Test
    void lineCommentRequiresSpaceAfterWhenConfigured() {
        SqlParser p = SqlParsers.lineComment("--", true);
        assertThat(p.parse("-- ok\n", 0)).isEqualTo(5);
        assertThat(p.parse("--noSpace\n", 0)).isEqualTo(-1);
    }

    @Test
    void lineCommentWithSpaceRequirementAcceptsNewlineOrEndImmediately() {
        SqlParser p = SqlParsers.lineComment("--", true);
        assertThat(p.parse("--\nX", 0)).isEqualTo(2);
        assertThat(p.parse("--", 0)).isEqualTo(2);
    }

    @Test
    void delimitedConsumesUntilClose() {
        SqlParser p = SqlParsers.delimited("/*", "*/");
        assertThat(p.parse("/* c */ rest", 0)).isEqualTo(7);
    }

    @Test
    void delimitedFailsWhenOpenAbsent() {
        SqlParser p = SqlParsers.delimited("/*", "*/");
        assertThat(p.parse("SELECT", 0)).isEqualTo(-1);
    }

    @Test
    void delimitedConsumesToEndWhenUnterminated() {
        SqlParser p = SqlParsers.delimited("/*", "*/");
        assertThat(p.parse("/* unterminated", 0)).isEqualTo(15);
    }

    @Test
    void standardRegionMatchesEachVariant() {
        SqlParser p = SqlParsers.standardRegion();
        assertThat(p.parse("'a''b' x", 0)).isEqualTo(6);
        assertThat(p.parse("\"id\" x", 0)).isEqualTo(4);
        assertThat(p.parse("-- c\nX", 0)).isEqualTo(4);
        assertThat(p.parse("/* c */X", 0)).isEqualTo(7);
        assertThat(p.parse("plain", 0)).isEqualTo(-1);
    }
}
