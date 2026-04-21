package io.github.kakusuke.migraphe.jdbc.schema;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.api.generator.SourceContext;
import java.util.Objects;

public final class JdbcSchemaSourcePlugin implements GeneratorSourcePlugin<JdbcSchemaInfo> {

    @Override
    public String type() {
        return "jdbc-schema";
    }

    @Override
    public Class<JdbcSchemaInfo> dataClass() {
        return JdbcSchemaInfo.class;
    }

    @Override
    public JdbcSchemaInfo extract(SourceContext context) {
        Environment environment =
                Objects.requireNonNull(
                        context.environment(), "Environment is required for jdbc-schema source");
        return new JdbcSchemaInfoProvider().getSchemaInfo(environment);
    }
}
