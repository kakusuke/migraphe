package io.github.kakusuke.migraphe.mysql.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MySQLSchemaSourcePluginTest {

    private final MySQLSchemaSourcePlugin plugin = new MySQLSchemaSourcePlugin();

    @Test
    void typeReturnsMysqlSchema() {
        assertThat(plugin.type()).isEqualTo("mysql-schema");
    }

    @Test
    void dataClassReturnsMySQLSchemaInfo() {
        assertThat(plugin.dataClass()).isEqualTo(MySQLSchemaInfo.class);
    }
}
