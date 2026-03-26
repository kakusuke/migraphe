package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import org.junit.jupiter.api.Test;

class JdbcEnvironmentTest {

    @Test
    void createWithAllFields() {
        var env =
                JdbcEnvironment.create(
                        "testdb", "jdbc:h2:mem:test1", "sa", "", "org.h2.Driver", "H2");
        assertThat(env.id().value()).isEqualTo("testdb");
        assertThat(env.name()).isEqualTo("testdb");
        assertThat(env.getJdbcUrl()).isEqualTo("jdbc:h2:mem:test1");
        assertThat(env.getUsername()).isEqualTo("sa");
        assertThat(env.getPassword()).isEmpty();
        assertThat(env.getDriverClassName()).isEqualTo("org.h2.Driver");
        assertThat(env.getDbLabel()).isEqualTo("H2");
    }

    @Test
    void createConnectionWithH2() throws Exception {
        var env =
                JdbcEnvironment.create(
                        "testdb",
                        "jdbc:h2:mem:conntest;DB_CLOSE_DELAY=-1",
                        "sa",
                        "",
                        "org.h2.Driver",
                        "H2");
        try (Connection conn = env.createConnection()) {
            assertThat(conn).isNotNull();
            assertThat(conn.isClosed()).isFalse();
        }
    }

    @Test
    void nullPasswordIsAllowed() {
        var env =
                JdbcEnvironment.create(
                        "testdb", "jdbc:h2:mem:test2", "sa", null, "org.h2.Driver", "H2");
        assertThat(env.getPassword()).isNull();
    }

    @Test
    void nullNameThrowsNPE() {
        assertThatThrownBy(
                        () ->
                                JdbcEnvironment.create(
                                        null, "jdbc:h2:mem:test3", "sa", "", "org.h2.Driver", "H2"))
                .isInstanceOf(NullPointerException.class);
    }
}
