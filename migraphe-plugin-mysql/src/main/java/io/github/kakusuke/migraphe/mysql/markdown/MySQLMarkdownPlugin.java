package io.github.kakusuke.migraphe.mysql.markdown;

import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;
import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownDefinition;
import io.github.kakusuke.migraphe.mysql.schema.MySQLSchemaInfo;
import java.util.List;

public final class MySQLMarkdownPlugin implements GeneratorOutputPlugin {

    @Override
    public String type() {
        return "mysql-markdown";
    }

    @Override
    public Class<? extends GeneratorDefinition> definitionClass() {
        return JdbcMarkdownDefinition.class;
    }

    @Override
    public boolean canHandle(Class<?> dataClass) {
        return MySQLSchemaInfo.class.isAssignableFrom(dataClass);
    }

    @Override
    public void output(Object data, OutputContext context) {
        var schemaInfo = (MySQLSchemaInfo) data;
        var definition = (JdbcMarkdownDefinition) context.definition();
        var excludes = definition.excludes().orElse(List.of());
        var generator = new MySQLMarkdownGenerator(definition.name(), schemaInfo, excludes);
        generator.generate(context.outputDir());
    }
}
