package io.github.kakusuke.migraphe.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MainVersionFlagTest {

    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void redirect() {
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restore() {
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("--version で migraphe <ver> (<commit>) を出力し exit 0")
    void shouldPrintVersionAndExitZeroForLongFlag() {
        int exitCode = Main.run(new String[] {"--version"});

        assertThat(exitCode).isZero();
        String out = captured.toString(StandardCharsets.UTF_8);
        assertThat(out).startsWith("migraphe ");
        assertThat(out).doesNotContain("unknown");
    }

    @Test
    @DisplayName("-v で --version と同じ振る舞いになる")
    void shouldPrintVersionAndExitZeroForShortFlag() {
        int exitCode = Main.run(new String[] {"-v"});

        assertThat(exitCode).isZero();
        String out = captured.toString(StandardCharsets.UTF_8);
        assertThat(out).startsWith("migraphe ");
        assertThat(out).doesNotContain("unknown");
    }
}
