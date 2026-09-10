package io.github.kakusuke.migraphe.api.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskTest {

    @Test
    void signatureIsNotInheritable() throws NoSuchMethodException {
        assertThat(Task.class.getMethod("signature").isDefault()).isFalse();
    }
}
