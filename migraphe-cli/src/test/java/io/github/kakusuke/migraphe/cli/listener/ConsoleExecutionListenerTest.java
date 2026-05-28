package io.github.kakusuke.migraphe.cli.listener;

import static org.assertj.core.api.Assertions.*;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.execution.ExecutionSummary;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.api.task.Task;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsoleExecutionListenerTest {

    private PrintStream originalOut;
    private ByteArrayOutputStream captured;
    private ConsoleExecutionListener listener;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        listener = new ConsoleExecutionListener(false);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    private String output() {
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    void onCompleted_failure_shouldRenderSummaryBlockWithAllFailures() {
        MigrationNode f1 = node("db1/003_create_orders", "Create orders", "postgres-main");
        MigrationNode f2 = node("db1/005_create_invoices", "Create invoices", "postgres-main");
        MigrationNode skipped = node("db1/006_seed_invoices", "Seed invoices", "postgres-main");

        listener.onNodeFailed(
                f1, ExecutionDirection.UP, null, "relation \"orders\" does not exist");
        listener.onNodeFailed(
                f2, ExecutionDirection.UP, null, "syntax error at or near \"INVALID\"");
        listener.onNodeSkipped(
                skipped, ExecutionDirection.UP, "dependency failed: 005_create_invoices");

        listener.onCompleted(ExecutionSummary.failure(ExecutionDirection.UP, 10, 6, 2, 2));

        String text = output();

        // ヘッダー + 集計
        assertThat(text).contains("=== MIGRATION SUMMARY (UP) ===");
        assertThat(text).contains("Result:    FAILED");
        assertThat(text).contains("Total:     10 nodes");
        assertThat(text).contains("Executed:   6 nodes");
        assertThat(text).contains("Skipped:    2 nodes");
        assertThat(text).contains("Failed:     2 nodes");

        // 失敗 2 件ともインデックス付きで載る
        assertThat(text).contains("[1] db1/003_create_orders");
        assertThat(text).contains("Environment: postgres-main");
        assertThat(text).contains("Error: relation \"orders\" does not exist");
        assertThat(text).contains("[2] db1/005_create_invoices");
        assertThat(text).contains("Error: syntax error at or near \"INVALID\"");

        // dep-skip 一覧
        assertThat(text).contains("Skipped due to failed dependencies:");
        assertThat(text).contains("db1/006_seed_invoices");
        assertThat(text).contains("dependency failed: 005_create_invoices");
    }

    @Test
    void onCompleted_success_shouldNotRenderSummaryBlock() {
        listener.onCompleted(ExecutionSummary.success(ExecutionDirection.UP, 3, 3, 0));

        String text = output();
        assertThat(text).doesNotContain("MIGRATION SUMMARY");
        assertThat(text).contains("Migration completed successfully");
    }

    @Test
    void onCompleted_failure_withNoFailureRecordsAccumulated_shouldStillRenderHeader() {
        // 直接 summary を渡したケース (failure 詳細がリスナーに通知されていない場合の安全網)
        listener.onCompleted(ExecutionSummary.failure(ExecutionDirection.UP, 1, 0, 0, 1));

        String text = output();
        assertThat(text).contains("=== MIGRATION SUMMARY (UP) ===");
        assertThat(text).contains("Result:    FAILED");
        assertThat(text).contains("Failed:     1 nodes");
    }

    private MigrationNode node(String id, String name, String envId) {
        return new TestNode(NodeId.of(id), name, EnvironmentId.of(envId));
    }

    private record TestNode(NodeId id, String name, EnvironmentId envId) implements MigrationNode {
        @Override
        public @Nullable String description() {
            return null;
        }

        @Override
        public Environment environment() {
            return new Environment() {
                @Override
                public EnvironmentId id() {
                    return envId;
                }

                @Override
                public String name() {
                    return envId.value();
                }
            };
        }

        @Override
        public Set<NodeId> dependencies() {
            return Set.of();
        }

        @Override
        public Task upTask() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable Task downTask() {
            return null;
        }
    }
}
