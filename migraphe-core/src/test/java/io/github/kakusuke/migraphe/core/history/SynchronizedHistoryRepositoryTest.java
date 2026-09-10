package io.github.kakusuke.migraphe.core.history;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.target.TargetId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SynchronizedHistoryRepository")
class SynchronizedHistoryRepositoryTest {

    private TargetId targetId;

    @BeforeEach
    void setUp() {
        targetId = TargetId.of("test");
    }

    @Nested
    @DisplayName("委譲")
    class Delegation {

        @Test
        @DisplayName("latestApplies() もデリゲートに委譲される — 上書きが迂回されない")
        void shouldDelegateLatestApplies() {
            // Given: a delegate that answers latestApplies() itself rather than folding
            // allRecords()
            ExecutionRecord marker =
                    ExecutionRecord.upSuccess(NodeId.of("marker"), targetId, "marker", null, 1L);
            InMemoryHistoryRepository backing = new InMemoryHistoryRepository();
            HistoryRepository delegate =
                    new HistoryRepository() {
                        @Override
                        public void initialize() {
                            backing.initialize();
                        }

                        @Override
                        public void record(ExecutionRecord record) {
                            backing.record(record);
                        }

                        @Override
                        public boolean wasExecuted(NodeId nodeId) {
                            return backing.wasExecuted(nodeId);
                        }

                        @Override
                        public List<NodeId> executedNodes() {
                            return backing.executedNodes();
                        }

                        @Override
                        public @Nullable ExecutionRecord findLatestRecord(NodeId nodeId) {
                            return backing.findLatestRecord(nodeId);
                        }

                        @Override
                        public List<ExecutionRecord> allRecords() {
                            return backing.allRecords();
                        }

                        @Override
                        public List<ExecutionRecord> latestApplies() {
                            return List.of(marker);
                        }
                    };
            HistoryRepository repo = new SynchronizedHistoryRepository(delegate);

            // When & Then
            assertThat(repo.latestApplies()).containsExactly(marker);
        }

        @Test
        @DisplayName("record() と wasExecuted() がデリゲートに委譲される")
        void shouldDelegateToWrappedRepository() {
            // Given
            InMemoryHistoryRepository delegate = new InMemoryHistoryRepository();
            HistoryRepository repo = new SynchronizedHistoryRepository(delegate);
            NodeId nodeId = NodeId.of("a");

            // When
            repo.record(ExecutionRecord.upSuccess(nodeId, targetId, "test", null, 100L));

            // Then
            assertThat(repo.wasExecuted(nodeId)).isTrue();
        }
    }

    @Nested
    @DisplayName("並行安全性")
    class ConcurrentSafety {

        @Test
        @DisplayName("10スレッドから同時に record() しても全件記録される")
        void shouldRecordAllEntriesUnderConcurrentWrites() throws Exception {
            // Given
            InMemoryHistoryRepository delegate = new InMemoryHistoryRepository();
            HistoryRepository repo = new SynchronizedHistoryRepository(delegate);

            // When
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    NodeId nodeId = NodeId.of("node-" + i);
                    futures.add(
                            executor.submit(
                                    () ->
                                            repo.record(
                                                    ExecutionRecord.upSuccess(
                                                            nodeId, targetId, "test", null,
                                                            100L))));
                }
                for (Future<?> future : futures) {
                    future.get();
                }
            }

            // Then
            assertThat(repo.allRecords()).hasSize(10);
        }
    }
}
