package io.github.kakusuke.migraphe.postgresql.markdown;

import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;
import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownDefinition;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLSchemaInfo;

public final class PostgreSQLMarkdownPlugin implements GeneratorOutputPlugin {

    @Override
    public String type() {
        return "postgresql-markdown";
    }

    @Override
    public Class<? extends GeneratorDefinition> definitionClass() {
        return JdbcMarkdownDefinition.class;
    }

    @Override
    public boolean canHandle(Class<?> dataClass) {
        return PostgreSQLSchemaInfo.class.isAssignableFrom(dataClass);
    }

    @Override
    public void output(Object data, OutputContext context) {
        var schemaInfo = (PostgreSQLSchemaInfo) data;
        var definition = (JdbcMarkdownDefinition) context.definition();
        var excludes = definition.excludes().orElse(java.util.List.of());
        var generator = new PostgreSQLMarkdownGenerator(definition.name(), schemaInfo, excludes);
        generator.generate(context.outputDir());
    }
}
