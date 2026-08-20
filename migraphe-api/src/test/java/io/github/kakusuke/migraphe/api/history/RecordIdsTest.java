package io.github.kakusuke.migraphe.api.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RecordIdsTest {

    @Nested
    @DisplayName("UUIDv7 としての妥当性")
    class Format {

        @Test
        @DisplayName("バージョン7・バリアント RFC 4122 の UUID を生成する")
        void generatesVersion7Uuid() {
            UUID uuid = UUID.fromString(RecordIds.newId());

            assertThat(uuid.version()).isEqualTo(7);
            assertThat(uuid.variant()).isEqualTo(2);
        }

        @Test
        @DisplayName("36文字の小文字16進正規表記で、既存の id 列長に収まる")
        void isCanonicalLowercase() {
            String id = RecordIds.newId();

            assertThat(id).hasSize(36).isLowerCase().matches("[0-9a-f-]{36}");
        }

        @Test
        @DisplayName("先頭48ビットが生成時刻のミリ秒を保持する")
        void embedsGenerationTimestamp() {
            // 共有インスタンスはカウンタ溢れで未来を先借りしている可能性があるため専用に生成する。
            RecordIds.Generator generator = new RecordIds.Generator(System::currentTimeMillis);

            long before = System.currentTimeMillis();
            UUID uuid = UUID.fromString(generator.next());
            long after = System.currentTimeMillis();

            long embedded = uuid.getMostSignificantBits() >>> 16;
            assertThat(embedded).isBetween(before, after);
        }
    }

    @Nested
    @DisplayName("順序性")
    class Ordering {

        @Test
        @DisplayName("生成順と辞書順が一致する")
        void lexicographicOrderMatchesGenerationOrder() {
            List<String> generated = new ArrayList<>();
            for (int i = 0; i < 1_000; i++) {
                generated.add(RecordIds.newId());
            }

            assertThat(generated).isSorted();
        }

        @Test
        @DisplayName("同一ミリ秒内で生成しても単調に増加する")
        void isMonotonicWithinTheSameMillisecond() {
            // 1ミリ秒に固定した時計で生成すれば、必ず同一タイムスタンプの組が生まれる。
            RecordIds.Generator generator = new RecordIds.Generator(() -> 1_700_000_000_000L);

            List<String> generated = new ArrayList<>();
            for (int i = 0; i < 1_000; i++) {
                generated.add(generator.next());
            }

            assertThat(generated).hasSizeGreaterThan(1);
            assertThat(sharesTimestamp(generated)).as("同一ミリ秒の組が含まれていないとこのテストは無意味").isTrue();
            assertThat(generated).isSorted();
        }

        private boolean sharesTimestamp(List<String> ids) {
            Set<Long> timestamps = new java.util.HashSet<>();
            for (String id : ids) {
                if (!timestamps.add(UUID.fromString(id).getMostSignificantBits() >>> 16)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Nested
    @DisplayName("並行生成")
    class Concurrency {

        @Test
        @DisplayName("多数スレッドから同時生成しても一意で、全体が単調に増加する")
        void isUniqueAndMonotonicUnderContention() throws Exception {
            int threads = 32;
            int perThread = 500;
            Set<String> ids = new ConcurrentSkipListSet<>();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);

            for (int t = 0; t < threads; t++) {
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try {
                                        start.await();
                                        for (int i = 0; i < perThread; i++) {
                                            ids.add(RecordIds.newId());
                                        }
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    } finally {
                                        done.countDown();
                                    }
                                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

            assertThat(ids).hasSize(threads * perThread);
        }
    }

    @Nested
    @DisplayName("時刻の巻き戻し")
    class ClockRegression {

        @Test
        @DisplayName("時刻が巻き戻っても直前の ID より大きい値を返す")
        void keepsIncreasingWhenTheClockGoesBackwards() {
            long start = 1_000_000L;
            // 3回目に大きく巻き戻る時計。
            long[] readings = {start, start + 1, start - 5_000, start - 5_000, start + 2};
            int[] index = {0};

            RecordIds.Generator generator =
                    new RecordIds.Generator(
                            () -> readings[Math.min(index[0]++, readings.length - 1)]);

            List<String> generated = new ArrayList<>();
            for (int i = 0; i < readings.length; i++) {
                generated.add(generator.next());
            }

            assertThat(generated).isSorted();
            assertThat(generated).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("巻き戻し中も UUIDv7 の形式を保つ")
        void staysAValidUuidWhileTheClockIsBehind() {
            long[] readings = {5_000L, 1L};
            int[] index = {0};
            RecordIds.Generator generator =
                    new RecordIds.Generator(
                            () -> readings[Math.min(index[0]++, readings.length - 1)]);

            generator.next();
            UUID second = UUID.fromString(generator.next());

            assertThat(second.version()).isEqualTo(7);
            assertThat(second.variant()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("ExecutionRecord との結線")
    class WiredIntoExecutionRecord {

        @Test
        @DisplayName("ファクトリが生成する id は時刻順に並ぶ")
        void factoryIdsAreTimeOrdered() {
            List<ExecutionRecord> records = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                records.add(
                        ExecutionRecord.upSuccess(
                                io.github.kakusuke.migraphe.api.graph.NodeId.of("n" + i),
                                io.github.kakusuke.migraphe.api.environment.EnvironmentId.of("t"),
                                "d",
                                null,
                                1));
            }

            assertThat(records)
                    .extracting(ExecutionRecord::id)
                    .isSortedAccordingTo(Comparator.naturalOrder());
            assertThat(records)
                    .allSatisfy(r -> assertThat(UUID.fromString(r.id()).version()).isEqualTo(7));
        }
    }
}
