package io.github.kakusuke.migraphe.core.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FormatUtils")
class FormatUtilsTest {

    @Nested
    @DisplayName("formatDuration")
    class FormatDuration {

        @Test
        @DisplayName("1000ms以上は秒単位で表示する")
        void shouldFormatAsSeconds() {
            assertThat(FormatUtils.formatDuration(1000)).isEqualTo("1.0s");
            assertThat(FormatUtils.formatDuration(1500)).isEqualTo("1.5s");
            assertThat(FormatUtils.formatDuration(12345)).isEqualTo("12.3s");
        }

        @Test
        @DisplayName("1000ms未満はミリ秒で表示する")
        void shouldFormatAsMilliseconds() {
            assertThat(FormatUtils.formatDuration(0)).isEqualTo("0ms");
            assertThat(FormatUtils.formatDuration(500)).isEqualTo("500ms");
            assertThat(FormatUtils.formatDuration(999)).isEqualTo("999ms");
        }
    }

    @Nested
    @DisplayName("formatDateTime")
    class FormatDateTime {

        @Test
        @DisplayName("Instant を yyyy-MM-dd HH:mm:ss でフォーマットする")
        void shouldFormatInstant() {
            // 期待するフォーマッタで同じ結果が得られることを検証
            DateTimeFormatter expected =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .withZone(ZoneId.systemDefault());
            Instant instant = Instant.parse("2026-01-30T12:34:56Z");

            String result = FormatUtils.formatDateTime(instant);

            assertThat(result).isEqualTo(expected.format(instant));
        }
    }
}
