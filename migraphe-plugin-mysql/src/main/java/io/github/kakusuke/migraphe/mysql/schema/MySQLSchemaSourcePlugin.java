package io.github.kakusuke.migraphe.mysql.schema;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.api.generator.SourceContext;
import java.util.Objects;

public final class MySQLSchemaSourcePlugin implements GeneratorSourcePlugin<MySQLSchemaInfo> {

    @Override
    public String type() {
        return "mysql-schema";
    }

    @Override
    public Class<MySQLSchemaInfo> dataClass() {
        return MySQLSchemaInfo.class;
    }

    @Override
    public MySQLSchemaInfo extract(SourceContext context) {
        Environment environment =
                Objects.requireNonNull(
                        context.environment(), "Environment is required for mysql-schema source");
        return new MySQLSchemaInfoProvider().getSchemaInfo(environment);
    }
}
