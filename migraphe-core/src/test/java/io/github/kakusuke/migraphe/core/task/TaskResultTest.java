package io.github.kakusuke.migraphe.core.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.task.TaskResult;
import org.junit.jupiter.api.Test;

class TaskResultTest {

    @Test
    void shouldCreateTaskResultWithoutDownTask() {
        // given
        String message = "Migration completed successfully";

        // when
        TaskResult result = TaskResult.withoutDownTask(message);

        // then
        assertThat(result.message()).isEqualTo(message);
        assertThat(result.serializedDownTask()).isNull();
    }

    @Test
    void shouldCreateTaskResultWithDownTask() {
        // given
        String message = "UP migration completed";
        String serializedDownTask = "{\"type\":\"rollback\",\"data\":\"...\" }";

        // when
        TaskResult result = TaskResult.withDownTask(message, serializedDownTask);

        // then
        assertThat(result.message()).isEqualTo(message);
        assertThat(result.serializedDownTask()).isEqualTo(serializedDownTask);
    }

    @Test
    void shouldThrowExceptionWhenMessageIsNull() {
        // when & then
        assertThatThrownBy(() -> TaskResult.withoutDownTask(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("message must not be null");
    }

    @Test
    void shouldThrowExceptionWhenSerializedDownTaskIsNull() {
        // when & then
        assertThatThrownBy(() -> TaskResult.withDownTask("message", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldAllowBlankMessage() {
        // when
        TaskResult result = TaskResult.withoutDownTask("");

        // then
        assertThat(result.message()).isEmpty();
    }
}
