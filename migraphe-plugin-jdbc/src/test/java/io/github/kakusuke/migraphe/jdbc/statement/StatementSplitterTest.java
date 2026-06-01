package io.github.kakusuke.migraphe.jdbc.statement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StatementSplitterTest {

    @Test
    void splitMultipleStatements() {
        StatementSplitter splitter = StatementSplitter.standard();
        List<String> result =
                splitter.split("CREATE TABLE t1 (id INT);\nCREATE TABLE t2 (id INT);\n");
        assertThat(result).containsExactly("CREATE TABLE t1 (id INT)", "CREATE TABLE t2 (id INT)");
    }

    @Test
    void splitSingleStatementWithTrailingNewline() {
        StatementSplitter splitter = StatementSplitter.standard();
        List<String> result = splitter.split("CREATE TABLE t (id INT);\n");
        assertThat(result).containsExactly("CREATE TABLE t (id INT)");
    }

    @Test
    void splitSingleStatementWithoutDelimiter() {
        StatementSplitter splitter = StatementSplitter.standard();
        List<String> result = splitter.split("SELECT 1");
        assertThat(result).containsExactly("SELECT 1");
    }

    @Test
    void splitExcludesEmptySegments() {
        StatementSplitter splitter = StatementSplitter.standard();
        List<String> result = splitter.split("CREATE TABLE t (id INT);\n\n;\n");
        assertThat(result).containsExactly("CREATE TABLE t (id INT)");
    }

    @Test
    void splitStatementFollowedByLineComment() {
        StatementSplitter splitter = StatementSplitter.standard();
        List<String> result =
                splitter.split("CREATE TABLE t1 (id INT); -- comment\nCREATE TABLE t2 (id INT);\n");
        assertThat(result)
                .containsExactly(
                        "CREATE TABLE t1 (id INT)", "-- comment\nCREATE TABLE t2 (id INT)");
    }

    @Test
    void splitDoesNotSplitOnSemicolonInsideStringLiteral() {
        StatementSplitter splitter = StatementSplitter.standard();
        List<String> result = splitter.split("INSERT INTO t VALUES ('a;b');\nSELECT 1;\n");
        assertThat(result).containsExactly("INSERT INTO t VALUES ('a;b')", "SELECT 1");
    }

    @Test
    void splitIgnoresSemicolonInsideLineComment() {
        StatementSplitter splitter = StatementSplitter.standard();
        List<String> result = splitter.split("SELECT 1; -- drop; everything\nSELECT 2;\n");
        assertThat(result).containsExactly("SELECT 1", "-- drop; everything\nSELECT 2");
    }

    @Test
    void splitIgnoresSemicolonInsideBlockComment() {
        StatementSplitter splitter = StatementSplitter.standard();
        List<String> result = splitter.split("SELECT 1 /* a;b */;\nSELECT 2;\n");
        assertThat(result).containsExactly("SELECT 1 /* a;b */", "SELECT 2");
    }

    @Test
    void splitKeepsTrailingLineCommentOnlySegment() {
        StatementSplitter splitter = StatementSplitter.standard();
        List<String> result = splitter.split("SELECT 1;\n-- tail comment\n");
        assertThat(result).containsExactly("SELECT 1", "-- tail comment");
    }

    @Test
    void splitKeepsLineCommentOnlyInput() {
        StatementSplitter splitter = StatementSplitter.standard();
        List<String> result = splitter.split("-- just a comment\n");
        assertThat(result).containsExactly("-- just a comment");
    }

    @Test
    void splitReturnsEmptyListForWhitespaceOnlyInput() {
        StatementSplitter splitter = StatementSplitter.standard();
        List<String> result = splitter.split("   \n\t ");
        assertThat(result).isEmpty();
    }

    @Test
    void splitRetainsLeadingBlockComment() {
        StatementSplitter splitter = StatementSplitter.standard();
        List<String> result = splitter.split("/* leading */ SELECT 1;\n");
        assertThat(result).containsExactly("/* leading */ SELECT 1");
    }

    @Test
    void splitKeepsLeadingLineCommentAttachedToStatement() {
        StatementSplitter splitter = StatementSplitter.standard();
        List<String> result = splitter.split("-- create t\nCREATE TABLE t (id INT);\n");
        assertThat(result).containsExactly("-- create t\nCREATE TABLE t (id INT)");
    }

    @Test
    void splitKeepsTrailingBlockCommentOnlySegment() {
        StatementSplitter splitter = StatementSplitter.standard();
        List<String> result = splitter.split("SELECT 1;\n/* tail */\n");
        assertThat(result).containsExactly("SELECT 1", "/* tail */");
    }

    @Test
    void splitWithMultiCharDelimiter() {
        StatementSplitter splitter = new StatementSplitter(SqlParsers.standardRegion(), "//", null);
        List<String> result = splitter.split("SELECT 1//SELECT 2//");
        assertThat(result).containsExactly("SELECT 1", "SELECT 2");
    }

    @Test
    void splitWithMultiCharDelimiterDoesNotSplitOnPartialMatch() {
        StatementSplitter splitter = new StatementSplitter(SqlParsers.standardRegion(), "//", null);
        List<String> result = splitter.split("SELECT 1/2//SELECT 3//");
        assertThat(result).containsExactly("SELECT 1/2", "SELECT 3");
    }

    @Test
    void splitWithDirectiveChangesDelimiterMidStream() {
        DelimiterDirective directive =
                (sql, pos) -> {
                    if (sql.startsWith("@@", pos)) {
                        return new DelimiterDirective.Result("//", pos + 2);
                    }
                    return null;
                };
        StatementSplitter splitter =
                new StatementSplitter(SqlParsers.standardRegion(), ";", directive);
        List<String> result = splitter.split("SELECT 1;@@SELECT 2; still one//SELECT 3//");
        assertThat(result).containsExactly("SELECT 1", "SELECT 2; still one", "SELECT 3");
    }

    @Test
    void splitDirectiveItselfIsNotEmitted() {
        DelimiterDirective directive =
                (sql, pos) -> {
                    if (sql.startsWith("@@", pos)) {
                        return new DelimiterDirective.Result(";", pos + 2);
                    }
                    return null;
                };
        StatementSplitter splitter =
                new StatementSplitter(SqlParsers.standardRegion(), ";", directive);
        List<String> result = splitter.split("@@SELECT 1;");
        assertThat(result).containsExactly("SELECT 1");
    }
}
