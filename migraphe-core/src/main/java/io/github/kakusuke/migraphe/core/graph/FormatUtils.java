package io.github.kakusuke.migraphe.core.graph;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 実行履歴の表示に使うフォーマットユーティリティ。 */
public final class FormatUtils {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private FormatUtils() {}

    /**
     * 所要時間をフォーマットする。
     *
     * @param durationMs ミリ秒単位の所要時間
     * @return フォーマットされた文字列（例: "1.2s", "500ms"）
     */
    public static String formatDuration(long durationMs) {
        if (durationMs >= 1000) {
            return String.format("%.1fs", durationMs / 1000.0);
        }
        return durationMs + "ms";
    }

    /**
     * 日時をフォーマットする。
     *
     * @param instant フォーマット対象の日時
     * @return フォーマットされた文字列（例: "2026-01-30 12:34:56"）
     */
    public static String formatDateTime(Instant instant) {
        return DATE_TIME_FORMATTER.format(instant);
    }
}
