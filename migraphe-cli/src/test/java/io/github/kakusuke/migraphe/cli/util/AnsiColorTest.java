package io.github.kakusuke.migraphe.cli.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnsiColorTest {

    @Test
    void shouldWrapTextInGreen() {
        // when
        String result = AnsiColor.green("OK");

        // then
        assertThat(result).isEqualTo("\u001B[32mOK\u001B[0m");
    }

    @Test
    void shouldWrapTextInYellow() {
        // when
        String result = AnsiColor.yellow("SKIP");

        // then
        assertThat(result).isEqualTo("\u001B[33mSKIP\u001B[0m");
    }

    @Test
    void shouldWrapTextInRed() {
        // when
        String result = AnsiColor.red("FAIL");

        // then
        assertThat(result).isEqualTo("\u001B[31mFAIL\u001B[0m");
    }

    @Test
    void shouldWrapTextInCyan() {
        // when
        String result = AnsiColor.cyan("info");

        // then
        assertThat(result).isEqualTo("\u001B[36minfo\u001B[0m");
    }

    @Test
    void shouldWrapTextInBold() {
        // when
        String result = AnsiColor.bold("title");

        // then
        assertThat(result).isEqualTo("\u001B[1mtitle\u001B[0m");
    }

    @Test
    void shouldStripColorsFromText() {
        // given
        String colored = AnsiColor.green("OK") + " " + AnsiColor.red("FAIL");

        // when
        String stripped = AnsiColor.stripColors(colored);

        // then
        assertThat(stripped).isEqualTo("OK FAIL");
    }

    @Test
    void shouldStripNestedColors() {
        // given
        String text = "\u001B[1m\u001B[32mbold green\u001B[0m\u001B[0m";

        // when
        String stripped = AnsiColor.stripColors(text);

        // then
        assertThat(stripped).isEqualTo("bold green");
    }

    @Test
    void shouldReturnPlainTextWhenColorsDisabled() {
        // when
        String result = AnsiColor.colorize("test", AnsiColor.GREEN, false);

        // then
        assertThat(result).isEqualTo("test");
    }

    @Test
    void shouldReturnColoredTextWhenColorsEnabled() {
        // when
        String result = AnsiColor.colorize("test", AnsiColor.GREEN, true);

        // then
        assertThat(result).isEqualTo("\u001B[32mtest\u001B[0m");
    }
}
