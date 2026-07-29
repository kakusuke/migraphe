package io.github.kakusuke.migraphe.jdbc.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;

class JdbcMarkdownDefinitionTest {

    private static JdbcMarkdownDefinition defaultDefinition() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withMapping(JdbcMarkdownDefinition.class)
                        .withDefaultValue("type", "jdbc-markdown")
                        .withDefaultValue("name", "mydb")
                        .build();
        return config.getConfigMapping(JdbcMarkdownDefinition.class);
    }

    @Test
    void typeIsRead() {
        assertThat(defaultDefinition().type()).isEqualTo("jdbc-markdown");
    }

    @Test
    void nameIsRead() {
        assertThat(defaultDefinition().name()).isEqualTo("mydb");
    }

    @Test
    void outputDirDefaultValue() {
        assertThat(defaultDefinition().outputDir()).isEqualTo("docs/schema");
    }

    @Test
    void erDiagramLayoutDefaultValue() {
        assertThat(defaultDefinition().erDiagramLayout()).isEqualTo("elk");
    }

    @Test
    void erDiagramPerTableDefaultValue() {
        assertThat(defaultDefinition().erDiagramPerTable()).isTrue();
    }

    @Test
    void erDiagramPerTableMaxEntitiesDefaultValue() {
        assertThat(defaultDefinition().erDiagramPerTableMaxEntities()).isEqualTo(60);
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
        assertThat(defaultDefinition().name()).isEqualTo("mydb");
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
