package io.github.kakusuke.migraphe.api.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import org.junit.jupiter.api.Test;

class SchemaInfoProviderTest {

    @Test
    void shouldReturnSchemaInfoFromEnvironment() {
        // given
        Environment environment =
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
        SchemaInfoProvider<String> provider = env -> env.name();

        // when
        String schemaInfo = provider.getSchemaInfo(environment);

        // then
        assertThat(schemaInfo).isEqualTo("test");
    }
}
