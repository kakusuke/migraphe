package io.github.kakusuke.migraphe.api.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import org.junit.jupiter.api.Test;

class SchemaInfoProviderTest {

    @Test
    void shouldReturnSchemaInfoFromTarget() {
        // given
        Target target =
                new Target() {
                    @Override
                    public TargetId id() {
                        return TargetId.of("test");
                    }

                    @Override
                    public String name() {
                        return "test";
                    }
                };
        SchemaInfoProvider<String> provider = source -> source.name();

        // when
        String schemaInfo = provider.getSchemaInfo(target);

        // then
        assertThat(schemaInfo).isEqualTo("test");
    }
}
