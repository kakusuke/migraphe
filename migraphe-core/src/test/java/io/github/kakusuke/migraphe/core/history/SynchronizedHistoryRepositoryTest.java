package io.github.kakusuke.migraphe.core.history;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SynchronizedHistoryRepository")
class SynchronizedHistoryRepositoryTest {

    private EnvironmentId envId;

    @BeforeEach
    void setUp() {
        envId = EnvironmentId.of("test");
    }

    @Nested
    @DisplayName("委譲")
    class Delegation {

        @Test
        @DisplayName("record() と wasExecuted() がデリゲートに委譲される")
        void shouldDelegateToWrappedRepository() {
            // Given
            InMemoryHistoryRepository delegate = new InMemoryHistoryRepository();
            HistoryRepository repo = new SynchronizedHistoryRepository(delegate);
            NodeId nodeId = NodeId.of("a");

            // When
            repo.record(ExecutionRecord.upSuccess(nodeId, envId, "test", null, 100L));

            // Then
            assertThat(repo.wasExecuted(nodeId, envId)).isTrue();
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
                                                            nodeId, envId, "test", null, 100L))));
                }
                for (Future<?> future : futures) {
                    future.get();
                }
            }

            // Then
            assertThat(repo.allRecords(envId)).hasSize(10);
        }
    }
}
