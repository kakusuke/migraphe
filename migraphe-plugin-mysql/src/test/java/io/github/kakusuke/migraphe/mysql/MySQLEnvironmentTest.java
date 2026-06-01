package io.github.kakusuke.migraphe.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.jdbc.JdbcEnvironment;
import org.junit.jupiter.api.Test;

class MySQLEnvironmentTest {

    @Test
    void shouldCreateWithAllFields() {
        // when
        var env =
                MySQLEnvironment.create(
                        "testdb", "jdbc:mysql://localhost:3306/mydb", "user", "pass");

        // then
        assertThat(env.id().value()).isEqualTo("testdb");
        assertThat(env.name()).isEqualTo("testdb");
        assertThat(env.getJdbcUrl()).isEqualTo("jdbc:mysql://localhost:3306/mydb");
        assertThat(env.getUsername()).isEqualTo("user");
        assertThat(env.getPassword()).isEqualTo("pass");
        assertThat(env.getDriverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(env.getDbLabel()).isEqualTo("MySQL");
    }

    @Test
    void shouldExtendJdbcEnvironment() {
        // when
        var env =
                MySQLEnvironment.create(
                        "testdb", "jdbc:mysql://localhost:3306/mydb", "user", "pass");

        // then
        assertThat(env).isInstanceOf(JdbcEnvironment.class);
    }

    @Test
    void shouldAllowNullPassword() {
        // when
        var env =
                MySQLEnvironment.create("testdb", "jdbc:mysql://localhost:3306/mydb", "user", null);

        // then
        assertThat(env.getPassword()).isNull();
    }

    @Test
    void shouldUseMySqlStatementSplitter() {
        // given
        var env =
                MySQLEnvironment.create(
                        "testdb", "jdbc:mysql://localhost:3306/mydb", "user", "pass");

        // when: MySQL ルーチン本体の内部 ; では分割されない
        var result =
                env.statementSplitter()
                        .split(
                                "CREATE PROCEDURE p() BEGIN INSERT INTO t VALUES(1); END;\n"
                                        + "SELECT 1;\n");

        // then
        assertThat(result)
                .containsExactly(
                        "CREATE PROCEDURE p() BEGIN INSERT INTO t VALUES(1); END", "SELECT 1");
    }
}
