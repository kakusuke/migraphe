package io.github.kakusuke.migraphe.postgresql.schema;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.api.generator.SourceContext;
import java.util.Objects;

public final class PostgreSQLSchemaSourcePlugin
        implements GeneratorSourcePlugin<PostgreSQLSchemaInfo> {

    @Override
    public String type() {
        return "postgresql-schema";
    }

    @Override
    public Class<PostgreSQLSchemaInfo> dataClass() {
        return PostgreSQLSchemaInfo.class;
    }

    @Override
    public PostgreSQLSchemaInfo extract(SourceContext context) {
        Environment environment =
                Objects.requireNonNull(
                        context.environment(),
                        "Environment is required for postgresql-schema source");
        return new PostgreSQLSchemaInfoProvider().getSchemaInfo(environment);
    }
}
