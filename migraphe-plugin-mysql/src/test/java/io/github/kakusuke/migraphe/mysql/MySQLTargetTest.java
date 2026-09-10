package io.github.kakusuke.migraphe.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.jdbc.JdbcTarget;
import org.junit.jupiter.api.Test;

class MySQLTargetTest {

    @Test
    void shouldCreateWithAllFields() {
        // when
        var target =
                MySQLTarget.create("testdb", "jdbc:mysql://localhost:3306/mydb", "user", "pass");

        // then
        assertThat(target.id().value()).isEqualTo("testdb");
        assertThat(target.name()).isEqualTo("testdb");
        assertThat(target.getJdbcUrl()).isEqualTo("jdbc:mysql://localhost:3306/mydb");
        assertThat(target.getUsername()).isEqualTo("user");
        assertThat(target.getPassword()).isEqualTo("pass");
        assertThat(target.getDriverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(target.getDbLabel()).isEqualTo("MySQL");
    }

    @Test
    void shouldExtendJdbcTarget() {
        // when
        var target =
                MySQLTarget.create("testdb", "jdbc:mysql://localhost:3306/mydb", "user", "pass");

        // then
        assertThat(target).isInstanceOf(JdbcTarget.class);
    }

    @Test
    void shouldAllowNullPassword() {
        // when
        var target = MySQLTarget.create("testdb", "jdbc:mysql://localhost:3306/mydb", "user", null);

        // then
        assertThat(target.getPassword()).isNull();
    }

    @Test
    void shouldUseMySqlStatementSplitter() {
        // given
        var target =
                MySQLTarget.create("testdb", "jdbc:mysql://localhost:3306/mydb", "user", "pass");

        // when: MySQL ルーチン本体の内部 ; では分割されない
        var result =
                target.statementSplitter()
                        .split(
                                "CREATE PROCEDURE p() BEGIN INSERT INTO t VALUES(1); END;\n"
                                        + "SELECT 1;\n");

        // then
        assertThat(result)
                .containsExactly(
                        "CREATE PROCEDURE p() BEGIN INSERT INTO t VALUES(1); END", "SELECT 1");
    }
}
