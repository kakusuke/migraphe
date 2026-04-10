package io.github.kakusuke.migraphe.jdbc.markdown;

import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import java.util.List;

public final class JdbcMarkdownPlugin implements GeneratorOutputPlugin {

    @Override
    public String type() {
        return "jdbc-markdown";
    }

    @Override
    public Class<? extends GeneratorDefinition> definitionClass() {
        return JdbcMarkdownDefinition.class;
    }

    @Override
    public boolean canHandle(Class<?> dataClass) {
        return JdbcSchemaInfo.class.isAssignableFrom(dataClass);
    }

    @Override
    public void output(Object data, OutputContext context) {
        var schemaInfo = (JdbcSchemaInfo) data;
        var definition = (JdbcMarkdownDefinition) context.definition();
        var generator =
                new JdbcMarkdownGenerator(
                        definition.name(), schemaInfo, resolveExcludes(definition));
        generator.generate(context.outputDir());
    }

    private static List<JdbcMarkdownDefinition.ExcludePattern> resolveExcludes(
            JdbcMarkdownDefinition definition) {
        return definition.excludes().orElse(List.of());
    }
}
