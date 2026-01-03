package io.github.migraphe.cli.config;

import static org.assertj.core.api.Assertions.*;

import io.github.migraphe.cli.exception.ConfigurationException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VariableExpanderTest {

    @Test
    void shouldExpandSimpleVariable() {
        String input = "jdbc:postgresql://${DB_HOST}:5432/mydb";
        Map<String, String> vars = Map.of("DB_HOST", "localhost");

        String result = VariableExpander.expand(input, vars);

        assertThat(result).isEqualTo("jdbc:postgresql://localhost:5432/mydb");
    }

    @Test
    void shouldExpandMultipleVariables() {
        String input = "jdbc:postgresql://${DB_HOST}:${DB_PORT}/mydb";
        Map<String, String> vars = Map.of("DB_HOST", "localhost", "DB_PORT", "5432");

        String result = VariableExpander.expand(input, vars);

        assertThat(result).isEqualTo("jdbc:postgresql://localhost:5432/mydb");
    }

    @Test
    void shouldExpandNestedVariables() {
        String input = "${URL}";
        Map<String, String> vars = Map.of(
                "URL", "jdbc:postgresql://${HOST}:5432",
                "HOST", "localhost"
        );

        String result = VariableExpander.expand(input, vars);

        assertThat(result).isEqualTo("jdbc:postgresql://localhost:5432");
    }

    @Test
    void shouldExpandDeeplyNestedVariables() {
        String input = "${A}";
        Map<String, String> vars = Map.of(
                "A", "${B}/path",
                "B", "${C}",
                "C", "value"
        );

        String result = VariableExpander.expand(input, vars);

        assertThat(result).isEqualTo("value/path");
    }

    @Test
    void shouldThrowExceptionForUndefinedVariable() {
        String input = "${UNDEFINED_VAR}";
        Map<String, String> vars = Map.of();

        assertThatThrownBy(() -> VariableExpander.expand(input, vars))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Undefined variable")
                .hasMessageContaining("UNDEFINED_VAR");
    }

    @Test
    void shouldThrowExceptionForCircularReference() {
        String input = "${A}";
        Map<String, String> vars = Map.of("A", "${B}", "B", "${A}");

        assertThatThrownBy(() -> VariableExpander.expand(input, vars))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Maximum expansion depth exceeded");
    }

    @Test
    void shouldThrowExceptionForIndirectCircularReference() {
        String input = "${A}";
        Map<String, String> vars = Map.of("A", "${B}", "B", "${C}", "C", "${A}");

        assertThatThrownBy(() -> VariableExpander.expand(input, vars))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Maximum expansion depth exceeded");
    }

    @Test
    void shouldHandleNoVariables() {
        String input = "plain text without variables";
        Map<String, String> vars = Map.of();

        String result = VariableExpander.expand(input, vars);

        assertThat(result).isEqualTo("plain text without variables");
    }

    @Test
    void shouldHandleNullInput() {
        String result = VariableExpander.expand(null, Map.of());

        assertThat(result).isNull();
    }

    @Test
    void shouldHandleEmptyString() {
        String result = VariableExpander.expand("", Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldPreserveTextWithoutDollarSign() {
        String input = "no variables here";
        Map<String, String> vars = Map.of("KEY", "value");

        String result = VariableExpander.expand(input, vars);

        assertThat(result).isEqualTo("no variables here");
    }

    @Test
    void shouldExpandSameVariableMultipleTimes() {
        String input = "${VAR}/${VAR}/${VAR}";
        Map<String, String> vars = Map.of("VAR", "value");

        String result = VariableExpander.expand(input, vars);

        assertThat(result).isEqualTo("value/value/value");
    }

    @Test
    void shouldHandleVariableWithUnderscoreAndNumbers() {
        String input = "${DB_HOST_1}";
        Map<String, String> vars = Map.of("DB_HOST_1", "localhost");

        String result = VariableExpander.expand(input, vars);

        assertThat(result).isEqualTo("localhost");
    }

    @Test
    void shouldThrowExceptionWhenExceedingMaxDepth() {
        String input = "${A}";
        Map<String, String> vars = Map.ofEntries(
                Map.entry("A", "${B}"),
                Map.entry("B", "${C}"),
                Map.entry("C", "${D}"),
                Map.entry("D", "${E}"),
                Map.entry("E", "${F}"),
                Map.entry("F", "${G}"),
                Map.entry("G", "${H}"),
                Map.entry("H", "${I}"),
                Map.entry("I", "${J}"),
                Map.entry("J", "${K}"),
                Map.entry("K", "${L}")  // 深すぎる
        );

        assertThatThrownBy(() -> VariableExpander.expand(input, vars))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Maximum expansion depth exceeded");
    }
}
