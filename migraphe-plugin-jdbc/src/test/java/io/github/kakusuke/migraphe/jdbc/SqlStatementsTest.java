package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SqlStatementsTest {

    @Test
    void splitSingleStatement() {
        String[] result = SqlStatements.splitStatements("CREATE TABLE t (id INT);\n");
        assertThat(result).containsExactly("CREATE TABLE t (id INT)");
    }

    @Test
    void splitMultipleStatements() {
        String sql = "CREATE TABLE t1 (id INT);\nCREATE TABLE t2 (id INT);\n";
        String[] result = SqlStatements.splitStatements(sql);
        assertThat(result).containsExactly("CREATE TABLE t1 (id INT)", "CREATE TABLE t2 (id INT)");
    }

    @Test
    void splitWithComments() {
        String sql = "CREATE TABLE t1 (id INT); -- comment\nCREATE TABLE t2 (id INT);\n";
        String[] result = SqlStatements.splitStatements(sql);
        assertThat(result).containsExactly("CREATE TABLE t1 (id INT)", "CREATE TABLE t2 (id INT)");
    }

    @Test
    void splitIgnoresEmptyStatements() {
        String sql = "CREATE TABLE t (id INT);\n\n;\n";
        String[] result = SqlStatements.splitStatements(sql);
        assertThat(result).containsExactly("CREATE TABLE t (id INT)");
    }

    @Test
    void singleStatementWithoutTrailingNewline() {
        String[] result = SqlStatements.splitStatements("SELECT 1");
        assertThat(result).containsExactly("SELECT 1");
    }
}
