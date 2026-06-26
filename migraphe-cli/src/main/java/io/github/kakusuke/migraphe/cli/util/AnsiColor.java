package io.github.kakusuke.migraphe.cli.util;

import java.util.regex.Pattern;

/**
 * Utility for colorizing console output with ANSI escape codes.
 *
 * <p>Provides the raw ANSI color constants used by the CLI together with convenience methods that
 * wrap a string in a color and reset it afterwards. Callers typically gate colorization on {@link
 * #isColorEnabled()} (or pass the flag through {@link #colorize(String, String, boolean)}) so that
 * output stays plain when redirected to a file or when {@code NO_COLOR} is set.
 *
 * <p>This is a stateless utility class and cannot be instantiated.
 */
public final class AnsiColor {

    /** Regular expression that matches ANSI SGR escape sequences, used by {@link #stripColors}. */
    private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile("\u001B\\[[0-9;]*m");

    /** ANSI escape sequence that resets all styling back to the terminal default. */
    public static final String RESET = "\u001B[0m";

    /** ANSI escape sequence that sets the foreground color to green. */
    public static final String GREEN = "\u001B[32m";

    /** ANSI escape sequence that sets the foreground color to yellow. */
    public static final String YELLOW = "\u001B[33m";

    /** ANSI escape sequence that sets the foreground color to red. */
    public static final String RED = "\u001B[31m";

    /** ANSI escape sequence that sets the foreground color to cyan. */
    public static final String CYAN = "\u001B[36m";

    /** ANSI escape sequence that enables bold (bright) text. */
    public static final String BOLD = "\u001B[1m";

    private AnsiColor() {
        // Utility class; not instantiable.
    }

    /**
     * Wraps the given text in green, followed by a reset.
     *
     * @param text the text to colorize
     * @return the text prefixed with {@link #GREEN} and suffixed with {@link #RESET}
     */
    public static String green(String text) {
        return GREEN + text + RESET;
    }

    /**
     * Wraps the given text in yellow, followed by a reset.
     *
     * @param text the text to colorize
     * @return the text prefixed with {@link #YELLOW} and suffixed with {@link #RESET}
     */
    public static String yellow(String text) {
        return YELLOW + text + RESET;
    }

    /**
     * Wraps the given text in red, followed by a reset.
     *
     * @param text the text to colorize
     * @return the text prefixed with {@link #RED} and suffixed with {@link #RESET}
     */
    public static String red(String text) {
        return RED + text + RESET;
    }

    /**
     * Wraps the given text in cyan, followed by a reset.
     *
     * @param text the text to colorize
     * @return the text prefixed with {@link #CYAN} and suffixed with {@link #RESET}
     */
    public static String cyan(String text) {
        return CYAN + text + RESET;
    }

    /**
     * Wraps the given text in bold, followed by a reset.
     *
     * @param text the text to colorize
     * @return the text prefixed with {@link #BOLD} and suffixed with {@link #RESET}
     */
    public static String bold(String text) {
        return BOLD + text + RESET;
    }

    /**
     * Removes all ANSI escape sequences from the given text.
     *
     * @param text the text that may contain ANSI escape sequences
     * @return the text with every ANSI SGR escape sequence stripped out
     */
    public static String stripColors(String text) {
        return ANSI_ESCAPE_PATTERN.matcher(text).replaceAll("");
    }

    /**
     * Conditionally colorizes the given text.
     *
     * @param text the text to colorize
     * @param color the ANSI color escape sequence to apply (e.g. {@link #GREEN})
     * @param colorEnabled whether colorization should be applied
     * @return the colorized text when {@code colorEnabled} is {@code true}, otherwise {@code text}
     *     unchanged
     */
    public static String colorize(String text, String color, boolean colorEnabled) {
        if (colorEnabled) {
            return color + text + RESET;
        }
        return text;
    }

    /**
     * Determines whether colorized output should be emitted.
     *
     * <p>Returns {@code true} only when the {@code NO_COLOR} environment variable is unset and a
     * system console is attached (i.e. output is an interactive terminal rather than a redirected
     * stream).
     *
     * @return {@code true} if colorized output is appropriate, {@code false} otherwise
     */
    public static boolean isColorEnabled() {
        return System.getenv("NO_COLOR") == null && System.console() != null;
    }
}
