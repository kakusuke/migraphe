package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;

class SqlTaskDefinitionTest {

    @Test
    void autocommitTrue() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withMapping(SqlTaskDefinition.class)
                        .withDefaultValue("name", "create_db")
                        .withDefaultValue("target", "admin")
                        .withDefaultValue("up", "CREATE DATABASE myapp")
                        .withDefaultValue("autocommit", "true")
                        .build();

        SqlTaskDefinition def = config.getConfigMapping(SqlTaskDefinition.class);
        assertThat(def.name()).isEqualTo("create_db");
        assertThat(def.autocommit()).hasValue(true);
    }

    @Test
    void autocommitFalse() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withMapping(SqlTaskDefinition.class)
                        .withDefaultValue("name", "create_table")
                        .withDefaultValue("target", "db1")
                        .withDefaultValue("up", "CREATE TABLE t1 (id INT)")
                        .withDefaultValue("autocommit", "false")
                        .build();

        SqlTaskDefinition def = config.getConfigMapping(SqlTaskDefinition.class);
        assertThat(def.autocommit()).hasValue(false);
    }

    @Test
    void autocommitDefault() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withMapping(SqlTaskDefinition.class)
                        .withDefaultValue("name", "create_table")
                        .withDefaultValue("target", "db1")
                        .withDefaultValue("up", "CREATE TABLE t1 (id INT)")
                        .build();

        SqlTaskDefinition def = config.getConfigMapping(SqlTaskDefinition.class);
        assertThat(def.autocommit()).isEmpty();
    }
}
