package io.github.kakusuke.migraphe.core.graph;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Formatting helpers for presenting execution-history information.
 *
 * <p>Provides small, stateless utilities used when rendering migration run output: human-readable
 * durations and timestamps. This class is not instantiable.
 */
public final class FormatUtils {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private FormatUtils() {}

    /**
     * Formats an elapsed duration for display.
     *
     * <p>Durations of one second or longer are rendered in seconds with one decimal place (for
     * example {@code "1.2s"}); shorter durations are rendered in whole milliseconds (for example
     * {@code "500ms"}).
     *
     * @param durationMs the elapsed time in milliseconds
     * @return a human-readable duration string such as {@code "1.2s"} or {@code "500ms"}
     */
    public static String formatDuration(long durationMs) {
        if (durationMs >= 1000) {
            return String.format("%.1fs", durationMs / 1000.0);
        }
        return durationMs + "ms";
    }

    /**
     * Formats an instant as a local date-time for display.
     *
     * <p>The instant is rendered in the system default time zone using the pattern {@code
     * "yyyy-MM-dd HH:mm:ss"} (for example {@code "2026-01-30 12:34:56"}).
     *
     * @param instant the point in time to format
     * @return the formatted date-time string in the system default time zone
     */
    public static String formatDateTime(Instant instant) {
        return DATE_TIME_FORMATTER.format(instant);
    }
}
