package io.github.kakusuke.migraphe.postgresql.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.postgresql.PostgreSQLEnvironment;
import io.github.kakusuke.migraphe.postgresql.PostgreSQLException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgreSQLSchemaInfoProviderTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine")
                    .waitingFor(new HostPortWaitStrategy().forPorts(5432));

    @Test
    void shouldExtractExtensions() {
        var env =
                PostgreSQLEnvironment.create(
                        "test",
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword());
        var provider = new PostgreSQLSchemaInfoProvider();

        var info = provider.getSchemaInfo(env);

        assertThat(info.extensions()).anyMatch(e -> e.name().equals("plpgsql"));
    }

    @Test
    void shouldExtractEnumTypes() throws Exception {
        executeSql("CREATE TYPE mood AS ENUM ('happy', 'sad', 'ok')");

        var info = new PostgreSQLSchemaInfoProvider().getSchemaInfo(createEnv());

        assertThat(info.enums()).anyMatch(e -> e.name().equals("mood"));
        var mood =
                info.enums().stream()
                        .filter(e -> e.name().equals("mood"))
                        .findFirst()
                        .orElseThrow();
        assertThat(mood.labels()).containsExactly("happy", "sad", "ok");
    }

    @Test
    void shouldExtractSequences() throws Exception {
        executeSql(
                "CREATE SEQUENCE test_seq START 10 INCREMENT 5 MINVALUE 1 MAXVALUE 1000 NO CYCLE");

        var info = new PostgreSQLSchemaInfoProvider().getSchemaInfo(createEnv());

        assertThat(info.sequences()).anyMatch(s -> s.name().equals("test_seq"));
        var seq =
                info.sequences().stream()
                        .filter(s -> s.name().equals("test_seq"))
                        .findFirst()
                        .orElseThrow();
        assertThat(seq.startValue()).isEqualTo(10);
        assertThat(seq.increment()).isEqualTo(5);
        assertThat(seq.cycle()).isFalse();
    }

    @Test
    void shouldExtractFunctions() throws Exception {
        executeSql(
                "CREATE FUNCTION add_numbers(a integer, b integer) RETURNS integer AS 'SELECT a +"
                        + " b' LANGUAGE sql");

        var info = new PostgreSQLSchemaInfoProvider().getSchemaInfo(createEnv());

        assertThat(info.functions()).anyMatch(f -> f.name().equals("add_numbers"));
        var func =
                info.functions().stream()
                        .filter(f -> f.name().equals("add_numbers"))
                        .findFirst()
                        .orElseThrow();
        assertThat(func.language()).isEqualTo("sql");
        assertThat(func.isProcedure()).isFalse();
    }

    @Test
    void shouldExtractTriggers() throws Exception {
        executeSql("CREATE TABLE trigger_test (id serial PRIMARY KEY, name text)");
        executeSql(
                "CREATE FUNCTION trigger_func() RETURNS trigger AS 'BEGIN RETURN NEW; END'"
                        + " LANGUAGE plpgsql");
        executeSql(
                "CREATE TRIGGER test_trigger AFTER INSERT ON trigger_test FOR EACH ROW EXECUTE"
                        + " FUNCTION trigger_func()");

        var info = new PostgreSQLSchemaInfoProvider().getSchemaInfo(createEnv());

        assertThat(info.triggers()).anyMatch(t -> t.name().equals("test_trigger"));
        var trigger =
                info.triggers().stream()
                        .filter(t -> t.name().equals("test_trigger"))
                        .findFirst()
                        .orElseThrow();
        assertThat(trigger.timing()).isEqualTo("AFTER");
        assertThat(trigger.events()).contains("INSERT");
        assertThat(trigger.tableName()).isEqualTo("trigger_test");
    }

    @Test
    void shouldExtractMaterializedViews() throws Exception {
        executeSql("CREATE TABLE matview_source (id serial PRIMARY KEY, value int)");
        executeSql(
                "CREATE MATERIALIZED VIEW test_matview AS SELECT count(*) AS cnt FROM"
                        + " matview_source");

        var info = new PostgreSQLSchemaInfoProvider().getSchemaInfo(createEnv());

        assertThat(info.materializedViews()).anyMatch(mv -> mv.name().equals("test_matview"));
        var mv =
                info.materializedViews().stream()
                        .filter(m -> m.name().equals("test_matview"))
                        .findFirst()
                        .orElseThrow();
        assertThat(mv.schema()).isEqualTo("public");
    }

    @Test
    void shouldExtractPartitions() throws Exception {
        executeSql(
                "CREATE TABLE measurements (id serial, ts timestamp NOT NULL) PARTITION BY RANGE"
                        + " (ts)");

        var info = new PostgreSQLSchemaInfoProvider().getSchemaInfo(createEnv());

        assertThat(info.partitions()).anyMatch(p -> p.name().equals("measurements"));
        var part =
                info.partitions().stream()
                        .filter(p -> p.name().equals("measurements"))
                        .findFirst()
                        .orElseThrow();
        assertThat(part.strategy()).isEqualTo("RANGE");
    }

    @Test
    void shouldExtractPolicies() throws Exception {
        executeSql("CREATE TABLE policy_test (id serial PRIMARY KEY, owner text)");
        executeSql("ALTER TABLE policy_test ENABLE ROW LEVEL SECURITY");
        executeSql("CREATE POLICY owner_only ON policy_test USING (owner = current_user)");

        var info = new PostgreSQLSchemaInfoProvider().getSchemaInfo(createEnv());

        assertThat(info.policies()).anyMatch(p -> p.name().equals("owner_only"));
        var policy =
                info.policies().stream()
                        .filter(p -> p.name().equals("owner_only"))
                        .findFirst()
                        .orElseThrow();
        assertThat(policy.tableName()).isEqualTo("policy_test");
        assertThat(policy.command()).isEqualTo("ALL");
    }

    @Test
    void shouldThrowWhenNotPostgreSQLEnvironment() {
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
        var provider = new PostgreSQLSchemaInfoProvider();

        assertThatThrownBy(() -> provider.getSchemaInfo(env))
                .isInstanceOf(PostgreSQLException.class);
    }

    private PostgreSQLEnvironment createEnv() {
        return PostgreSQLEnvironment.create(
                "test", postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private void executeSql(String sql) throws Exception {
        try (var conn = createEnv().createConnection();
                var stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
