package io.github.kakusuke.migraphe.mysql.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.mysql.MySQLEnvironment;
import io.github.kakusuke.migraphe.mysql.MySQLException;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MySQLSchemaInfoProviderTest {

    @Container
    static MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("migraphe_test")
                    .withCommand("--log-bin-trust-function-creators=1", "--event-scheduler=ON");

    private MySQLEnvironment createEnv() {
        return MySQLEnvironment.create(
                "test", mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    private void executeSql(String sql) throws Exception {
        var env = createEnv();
        try (Connection conn = env.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    @Test
    void shouldThrowWhenNotMySQLEnvironment() {
        var env =
                new Environment() {
                    @Override
                    public EnvironmentId id() {
                        return EnvironmentId.of("test");
                    }

                    @Override
                    public String name() {
                        return "test";
                    }
                };
        var provider = new MySQLSchemaInfoProvider();

        assertThatThrownBy(() -> provider.getSchemaInfo(env)).isInstanceOf(MySQLException.class);
    }

    @Test
    void shouldExtractBaseSchemaFromJdbc() throws Exception {
        executeSql(
                "CREATE TABLE IF NOT EXISTS test_users ("
                        + "id INT PRIMARY KEY AUTO_INCREMENT,"
                        + "name VARCHAR(100) NOT NULL,"
                        + "email VARCHAR(200))");
        var provider = new MySQLSchemaInfoProvider();

        var info = provider.getSchemaInfo(createEnv());

        assertThat(info.schemas()).isNotEmpty();
        assertThat(info.schemas()).anyMatch(s -> s.name().equals("migraphe_test"));
        var schema =
                info.schemas().stream()
                        .filter(s -> s.name().equals("migraphe_test"))
                        .findFirst()
                        .orElseThrow();
        assertThat(schema.tables()).anyMatch(t -> t.name().equals("test_users"));
        var table =
                schema.tables().stream()
                        .filter(t -> t.name().equals("test_users"))
                        .findFirst()
                        .orElseThrow();
        assertThat(table.columns()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void shouldExtractStorageEngines() {
        var provider = new MySQLSchemaInfoProvider();

        var info = provider.getSchemaInfo(createEnv());

        assertThat(info.storageEngines()).isNotEmpty();
        assertThat(info.storageEngines())
                .anyMatch(e -> e.name().equals("InnoDB") && e.support().equals("DEFAULT"));
    }

    @Test
    void shouldExtractTableMeta() throws Exception {
        executeSql("CREATE TABLE IF NOT EXISTS meta_test (" + "id INT PRIMARY KEY) ENGINE=InnoDB");
        var provider = new MySQLSchemaInfoProvider();

        var info = provider.getSchemaInfo(createEnv());

        assertThat(info.tableMeta()).isNotEmpty();
        assertThat(info.tableMeta())
                .anyMatch(
                        m ->
                                m.tableName().equals("meta_test")
                                        && m.engine().equals("InnoDB")
                                        && m.schema().equals("migraphe_test"));
    }

    @Test
    void shouldExtractTriggers() throws Exception {
        executeSql(
                "CREATE TABLE IF NOT EXISTS trg_test (" + "id INT PRIMARY KEY, val INT DEFAULT 0)");
        executeSql("DROP TRIGGER IF EXISTS trg_test_insert");
        executeSql(
                "CREATE TRIGGER trg_test_insert BEFORE INSERT ON trg_test"
                        + " FOR EACH ROW SET NEW.val = NEW.val + 1");
        var provider = new MySQLSchemaInfoProvider();

        var info = provider.getSchemaInfo(createEnv());

        assertThat(info.triggers()).isNotEmpty();
        assertThat(info.triggers())
                .anyMatch(
                        t ->
                                t.name().equals("trg_test_insert")
                                        && t.timing().equals("BEFORE")
                                        && t.event().equals("INSERT")
                                        && t.tableName().equals("trg_test"));
    }

    @Test
    void shouldExtractRoutines() throws Exception {
        executeSql("DROP PROCEDURE IF EXISTS test_proc");
        executeSql("CREATE PROCEDURE test_proc(IN x INT) BEGIN SELECT x; END");
        var provider = new MySQLSchemaInfoProvider();

        var info = provider.getSchemaInfo(createEnv());

        assertThat(info.routines()).isNotEmpty();
        assertThat(info.routines())
                .anyMatch(
                        r ->
                                r.name().equals("test_proc")
                                        && r.type().equals("PROCEDURE")
                                        && r.schema().equals("migraphe_test"));
    }

    @Test
    void shouldExtractEvents() throws Exception {
        executeSql("DROP EVENT IF EXISTS test_event");
        executeSql("CREATE EVENT test_event ON SCHEDULE EVERY 1 DAY" + " DO SELECT 1");
        var provider = new MySQLSchemaInfoProvider();

        var info = provider.getSchemaInfo(createEnv());

        assertThat(info.events()).isNotEmpty();
        assertThat(info.events())
                .anyMatch(
                        e ->
                                e.name().equals("test_event")
                                        && e.type().equals("RECURRING")
                                        && e.schema().equals("migraphe_test"));
    }

    @Test
    void shouldExtractPartitions() throws Exception {
        executeSql("DROP TABLE IF EXISTS part_test");
        executeSql(
                "CREATE TABLE part_test ("
                        + "id INT, created_date DATE, PRIMARY KEY (id, created_date))"
                        + " PARTITION BY RANGE (YEAR(created_date)) ("
                        + "PARTITION p2024 VALUES LESS THAN (2025),"
                        + "PARTITION p2025 VALUES LESS THAN (2026))");
        var provider = new MySQLSchemaInfoProvider();

        var info = provider.getSchemaInfo(createEnv());

        assertThat(info.partitions()).isNotEmpty();
        assertThat(info.partitions())
                .anyMatch(
                        p ->
                                p.tableName().equals("part_test")
                                        && p.partitionMethod().equals("RANGE")
                                        && p.partitionCount() == 2);
    }
}
