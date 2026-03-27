package io.github.kakusuke.migraphe.jdbc.markdown;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.generator.api.Generator;
import io.github.kakusuke.migraphe.generator.api.GeneratorDefinition;
import io.github.kakusuke.migraphe.generator.api.GeneratorPlugin;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfoProvider;
import java.util.List;

public final class JdbcMarkdownPlugin implements GeneratorPlugin {

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
        JdbcSchemaInfo schemaInfo = schemaInfoProvider.getSchemaInfo(environment);
        List<JdbcMarkdownDefinition.ExcludePattern> excludes =
                jdbcDefinition.excludes().orElse(List.of());
        return new JdbcMarkdownGenerator(jdbcDefinition.name(), schemaInfo, excludes);
    }
}
