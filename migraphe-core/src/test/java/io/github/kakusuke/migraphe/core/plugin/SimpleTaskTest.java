package io.github.kakusuke.migraphe.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimpleTaskTest {

    @Test
    void signatureIsRenderedWhenItIsAskedFor_notWhenTheTaskIsBuilt() {
        assertThatCode(() -> SimpleTask.of(new Object())).doesNotThrowAnyException();

        assertThatThrownBy(() -> SimpleTask.of(new Object()).signature())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Object");
    }

    @Test
    void signatureKeepsAListsOrderAndIgnoresASetsOrMapsIterationOrder() {
        assertThat(SimpleTask.of(List.of("a", "b")).signature())
                .isNotEqualTo(SimpleTask.of(List.of("b", "a")).signature());

        assertThat(SimpleTask.of(new LinkedHashSet<>(List.of("a", "b"))).signature())
                .isEqualTo(SimpleTask.of(new LinkedHashSet<>(List.of("b", "a"))).signature());

        Map<String, String> oneOrder = new LinkedHashMap<>();
        oneOrder.put("k1", "v1");
        oneOrder.put("k2", "v2");
        Map<String, String> otherOrder = new LinkedHashMap<>();
        otherOrder.put("k2", "v2");
        otherOrder.put("k1", "v1");

        assertThat(SimpleTask.of(oneOrder).signature())
                .isEqualTo(SimpleTask.of(otherOrder).signature());
    }

    @Test
    void signatureTellsApartShapesThatWouldRenderAlikeUnbracketed() {
        assertThat(SimpleTask.of(List.of("a", "b")).signature())
                .isNotEqualTo(SimpleTask.of(new LinkedHashSet<>(List.of("a", "b"))).signature());

        assertThat(SimpleTask.of("ab").signature())
                .isNotEqualTo(SimpleTask.of(List.of("a", "b")).signature());
    }

    @Test
    void signatureDescendsIntoNestedListsAndMaps() {
        assertThat(SimpleTask.of(Map.of("args", List.of("a", "b"))).signature())
                .isNotEqualTo(SimpleTask.of(Map.of("args", List.of("b", "a"))).signature());
    }

    @Test
    void signatureAcceptsTheScalarsYamlProducesBesidesStrings() {
        assertThat(SimpleTask.of(Map.of("retries", 3, "concurrently", true)).signature())
                .isNotEqualTo(
                        SimpleTask.of(Map.of("retries", 4, "concurrently", true)).signature());
    }

    @Test
    void signatureIgnoresSurroundingWhitespaceOnText() {
        assertThat(SimpleTask.of("  CREATE TABLE t  ").signature())
                .isEqualTo(SimpleTask.of("CREATE TABLE t").signature());
    }
}
