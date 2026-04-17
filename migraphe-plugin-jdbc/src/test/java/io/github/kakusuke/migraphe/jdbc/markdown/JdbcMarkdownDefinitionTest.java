package io.github.kakusuke.migraphe.jdbc.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;

class JdbcMarkdownDefinitionTest {

    @Test
    void typeIsRead() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withMapping(JdbcMarkdownDefinition.class)
                        .withDefaultValue("type", "jdbc-markdown")
                        .withDefaultValue("name", "mydb")
                        .build();

        JdbcMarkdownDefinition def = config.getConfigMapping(JdbcMarkdownDefinition.class);
        assertThat(def.type()).isEqualTo("jdbc-markdown");
    }

    @Test
    void nameIsRead() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withMapping(JdbcMarkdownDefinition.class)
                        .withDefaultValue("type", "jdbc-markdown")
                        .withDefaultValue("name", "mydb")
                        .build();

        JdbcMarkdownDefinition def = config.getConfigMapping(JdbcMarkdownDefinition.class);
        assertThat(def.name()).isEqualTo("mydb");
    }

    @Test
    void outputDirDefaultValue() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withMapping(JdbcMarkdownDefinition.class)
                        .withDefaultValue("type", "jdbc-markdown")
                        .withDefaultValue("name", "mydb")
                        .build();

        JdbcMarkdownDefinition def = config.getConfigMapping(JdbcMarkdownDefinition.class);
        assertThat(def.outputDir()).isEqualTo("docs/schema");
    }

    @Test
    void outputDirCustomValue() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withMapping(JdbcMarkdownDefinition.class)
                        .withDefaultValue("type", "jdbc-markdown")
                        .withDefaultValue("name", "mydb")
                        .withDefaultValue("output-dir", "output/docs")
                        .build();

        JdbcMarkdownDefinition def = config.getConfigMapping(JdbcMarkdownDefinition.class);
        assertThat(def.outputDir()).isEqualTo("output/docs");
    }

    @Test
    void canBeBuiltWithoutTarget() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withMapping(JdbcMarkdownDefinition.class)
                        .withDefaultValue("type", "jdbc-markdown")
                        .withDefaultValue("name", "mydb")
                        .build();

        JdbcMarkdownDefinition def = config.getConfigMapping(JdbcMarkdownDefinition.class);
        assertThat(def.name()).isEqualTo("mydb");
    }

    @Test
    void excludesWithSchemaAndTable() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withMapping(JdbcMarkdownDefinition.class)
                        .withDefaultValue("type", "jdbc-markdown")
                        .withDefaultValue("name", "mydb")
                        .withDefaultValue("excludes[0].schema", "internal")
                        .withDefaultValue("excludes[0].table", "audit_log")
                        .build();

        JdbcMarkdownDefinition def = config.getConfigMapping(JdbcMarkdownDefinition.class);
        assertThat(def.excludes()).isPresent();
        assertThat(def.excludes().get()).hasSize(1);
        assertThat(def.excludes().get().get(0).schema()).hasValue("internal");
        assertThat(def.excludes().get().get(0).table()).hasValue("audit_log");
    }
}
