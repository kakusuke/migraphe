package io.github.kakusuke.migraphe.jdbc.markdown;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.generator.api.Generator;
import io.github.kakusuke.migraphe.generator.api.GeneratorDefinition;
import io.github.kakusuke.migraphe.generator.api.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.generator.api.GeneratorPlugin;
import io.github.kakusuke.migraphe.generator.api.OutputContext;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfoProvider;
import java.util.List;

public final class JdbcMarkdownPlugin implements GeneratorPlugin, GeneratorOutputPlugin {

    @Override
    public String type() {
        return "jdbc-markdown";
    }

    @Override
    public Class<? extends GeneratorDefinition> definitionClass() {
        return JdbcMarkdownDefinition.class;
    }

    @Override
    public Generator createGenerator(Environment environment, GeneratorDefinition definition) {
        var jdbcDefinition = (JdbcMarkdownDefinition) definition;
        var schemaInfoProvider = new JdbcSchemaInfoProvider();
        var schemaInfo = schemaInfoProvider.getSchemaInfo(environment);
        return new JdbcMarkdownGenerator(
                jdbcDefinition.name(), schemaInfo, resolveExcludes(jdbcDefinition));
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
