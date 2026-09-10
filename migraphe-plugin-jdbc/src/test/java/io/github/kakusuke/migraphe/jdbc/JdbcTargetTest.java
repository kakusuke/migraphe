package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.target.DownTaskRestorer;
import java.sql.Connection;
import org.junit.jupiter.api.Test;

class JdbcTargetTest {

    @Test
    void createWithAllFields() {
        var target =
                JdbcTarget.create("testdb", "jdbc:h2:mem:test1", "sa", "", "org.h2.Driver", "H2");
        assertThat(target.id().value()).isEqualTo("testdb");
        assertThat(target.name()).isEqualTo("testdb");
        assertThat(target.getJdbcUrl()).isEqualTo("jdbc:h2:mem:test1");
        assertThat(target.getUsername()).isEqualTo("sa");
        assertThat(target.getPassword()).isEmpty();
        assertThat(target.getDriverClassName()).isEqualTo("org.h2.Driver");
        assertThat(target.getDbLabel()).isEqualTo("H2");
    }

    @Test
    void restoresARecordedRollbackIncludingTheModeItNeeds() {
        var target =
                JdbcTarget.create(
                        "testdb", "jdbc:h2:mem:restore_test", "sa", "", "org.h2.Driver", "H2");

        assertThat(target).isInstanceOf(DownTaskRestorer.class);
        DownTaskRestorer restorer = (DownTaskRestorer) target;

        assertThat(restorer.restoreDownTask("DROP TABLE t", "autocommit.down=true\n").description())
                .isEqualTo("H2 DOWN migration (autocommit)");
        assertThat(
                        restorer.restoreDownTask("DROP TABLE t", "autocommit.down=false\n")
                                .description())
                .isEqualTo("H2 DOWN migration");
        assertThat(restorer.restoreDownTask("DROP TABLE t", null).description())
                .isEqualTo("H2 DOWN migration");
    }

    @Test
    void restoresTheModeTheUpTaskActuallyWroteDown() {
        var target =
                JdbcTarget.create(
                        "testdb",
                        "jdbc:h2:mem:restore_roundtrip;DB_CLOSE_DELAY=-1",
                        "sa",
                        "",
                        "org.h2.Driver",
                        "H2");

        String recordedByAnAutocommittingRollback =
                JdbcUpTask.create(
                                target, "CREATE TABLE rt1 (id INT)", "DROP TABLE rt1", false, true)
                        .execute()
                        .value()
                        .pluginMetadata();
        String recordedByATransactionalRollback =
                JdbcUpTask.create(
                                target, "CREATE TABLE rt2 (id INT)", "DROP TABLE rt2", false, false)
                        .execute()
                        .value()
                        .pluginMetadata();

        DownTaskRestorer restorer = (DownTaskRestorer) target;
        assertThat(
                        restorer.restoreDownTask(
                                        "DROP TABLE rt1", recordedByAnAutocommittingRollback)
                                .description())
                .isEqualTo("H2 DOWN migration (autocommit)");
        assertThat(
                        restorer.restoreDownTask("DROP TABLE rt2", recordedByATransactionalRollback)
                                .description())
                .isEqualTo("H2 DOWN migration");
    }

    @Test
    void createConnectionWithH2() throws Exception {
        var target =
                JdbcTarget.create(
                        "testdb",
                        "jdbc:h2:mem:conntest;DB_CLOSE_DELAY=-1",
                        "sa",
                        "",
                        "org.h2.Driver",
                        "H2");
        try (Connection conn = target.createConnection()) {
            assertThat(conn).isNotNull();
            assertThat(conn.isClosed()).isFalse();
        }
    }

    @Test
    void nullPasswordIsAllowed() {
        var target =
                JdbcTarget.create("testdb", "jdbc:h2:mem:test2", "sa", null, "org.h2.Driver", "H2");
        assertThat(target.getPassword()).isNull();
    }

    @Test
    void nullNameThrowsNPE() {
        assertThatThrownBy(
                        () ->
                                JdbcTarget.create(
                                        null, "jdbc:h2:mem:test3", "sa", "", "org.h2.Driver", "H2"))
                .isInstanceOf(NullPointerException.class);
    }
}
