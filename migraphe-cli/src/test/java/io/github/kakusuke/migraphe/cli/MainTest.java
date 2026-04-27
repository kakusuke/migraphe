package io.github.kakusuke.migraphe.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void shouldReturnExitCode1WhenNoArgsGiven() {
        int exitCode = Main.run(new String[0]);

        assertThat(exitCode).isEqualTo(1);
    }

    @Test
    void shouldNotPrintStackTraceForIllegalArgumentException() {
        assertThat(Main.shouldPrintStackTrace(new IllegalArgumentException("bad config")))
                .isFalse();
    }

    @Test
    void shouldPrintStackTraceForRuntimeException() {
        assertThat(Main.shouldPrintStackTrace(new RuntimeException("unexpected"))).isTrue();
    }

    @Test
    void handleExceptionShouldSuppressStackTraceForIllegalArgumentException() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.err;
        System.setErr(new PrintStream(baos));
        try {
            int code = Main.handleException(new IllegalArgumentException("bad config"));
            assertThat(code).isEqualTo(1);
            String stderr = baos.toString(StandardCharsets.UTF_8);
            assertThat(stderr).contains("Error: bad config");
            assertThat(stderr).doesNotContain("\tat ");
        } finally {
            System.setErr(original);
        }
    }
}
