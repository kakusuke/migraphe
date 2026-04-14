package io.github.kakusuke.migraphe.jdbc.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultJdbcSchemaInfoTest {

    @Test
    void shouldImplementJdbcSchemaInfoAndReturnSchemas() {
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public", List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        var defaultSchemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));

        assertThat(defaultSchemaInfo).isInstanceOf(JdbcSchemaInfo.class);
        assertThat(defaultSchemaInfo.schemas()).containsExactly(schemaDetail);
    }
}
