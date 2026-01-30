package io.github.kakusuke.migraphe.gradle;

import static org.assertj.core.api.Assertions.*;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.execution.ExecutionSummary;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.api.task.Task;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Marker;

class GradleExecutionListenerTest {

    private CapturingLogger capturingLogger;
    private GradleExecutionListener listener;

    @BeforeEach
    void setUp() {
        capturingLogger = new CapturingLogger();
        listener = new GradleExecutionListener(capturingLogger);
    }

    @Test
    void onNodeSucceeded_shouldLogWithOkStatus() {
        MigrationNode node = createTestNode("db1/create_users", "Create users table");

        listener.onNodeSucceeded(node, ExecutionDirection.UP, 42);

        assertThat(capturingLogger.lifecycleMessages).anyMatch(msg -> msg.contains("[OK]"));
        assertThat(capturingLogger.lifecycleMessages)
                .anyMatch(msg -> msg.contains("db1/create_users"));
    }

    @Test
    void onNodeSkipped_shouldLogWithSkipStatus() {
        MigrationNode node = createTestNode("db1/create_users", "Create users table");

        listener.onNodeSkipped(node, ExecutionDirection.UP, "already executed");

        assertThat(capturingLogger.lifecycleMessages).anyMatch(msg -> msg.contains("[SKIP]"));
        assertThat(capturingLogger.lifecycleMessages)
                .anyMatch(msg -> msg.contains("db1/create_users"));
    }

    @Test
    void onNodeFailed_shouldLogWithFailStatus() {
        MigrationNode node = createTestNode("db1/create_users", "Create users table");

        listener.onNodeFailed(node, ExecutionDirection.UP, null, "Connection refused");

        assertThat(capturingLogger.errorMessages).anyMatch(msg -> msg.contains("[FAIL]"));
        assertThat(capturingLogger.errorMessages)
                .anyMatch(msg -> msg.contains("Connection refused"));
    }

    @Test
    void onCompleted_success_shouldLogSuccessMessage() {
        ExecutionSummary summary = ExecutionSummary.success(ExecutionDirection.UP, 3, 3, 0);

        listener.onCompleted(summary);

        assertThat(capturingLogger.lifecycleMessages)
                .anyMatch(msg -> msg.contains("Migration") && msg.contains("successfully"));
    }

    @Test
    void onCompleted_successDown_shouldLogRollbackMessage() {
        ExecutionSummary summary = ExecutionSummary.success(ExecutionDirection.DOWN, 1, 1, 0);

        listener.onCompleted(summary);

        assertThat(capturingLogger.lifecycleMessages)
                .anyMatch(msg -> msg.contains("Rollback") && msg.contains("successfully"));
    }

    @Test
    void onCompleted_noExecutions_shouldLogUpToDate() {
        ExecutionSummary summary = ExecutionSummary.success(ExecutionDirection.UP, 0, 0, 0);

        listener.onCompleted(summary);

        assertThat(capturingLogger.lifecycleMessages).anyMatch(msg -> msg.contains("up to date"));
    }

    private MigrationNode createTestNode(String id, String name) {
        return new TestMigrationNode(NodeId.of(id), name, EnvironmentId.of("test-db"));
    }

    /** テスト用の MigrationNode 実装。 */
    private record TestMigrationNode(NodeId id, String name, EnvironmentId envId)
            implements MigrationNode {

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

    /**
     * ログメッセージをキャプチャするシンプルな Logger 実装。
     *
     * <p>Gradle の Logger インターフェースの最低限の実装。lifecycle と error メッセージを記録する。
     */
    private static class CapturingLogger implements Logger {

        final List<String> lifecycleMessages = new ArrayList<>();
        final List<String> errorMessages = new ArrayList<>();

        private String format(String pattern, Object... args) {
            String result = pattern;
            for (Object arg : args) {
                int idx = result.indexOf("{}");
                if (idx >= 0) {
                    result = result.substring(0, idx) + arg + result.substring(idx + 2);
                }
            }
            return result;
        }

        // lifecycle
        @Override
        public void lifecycle(String message) {
            lifecycleMessages.add(message);
        }

        @Override
        public void lifecycle(String message, Object... objects) {
            lifecycleMessages.add(format(message, objects));
        }

        @Override
        public void lifecycle(String message, Throwable throwable) {
            lifecycleMessages.add(message);
        }

        // error
        @Override
        public void error(String message) {
            errorMessages.add(message);
        }

        @Override
        public void error(String format, Object arg) {
            errorMessages.add(format(format, arg));
        }

        @Override
        public void error(String format, Object arg1, Object arg2) {
            errorMessages.add(format(format, arg1, arg2));
        }

        @Override
        public void error(String message, Object... objects) {
            errorMessages.add(format(message, objects));
        }

        @Override
        public void error(String message, Throwable throwable) {
            errorMessages.add(message);
        }

        // Remaining Logger interface methods - no-op stubs
        @Override
        public boolean isLifecycleEnabled() {
            return true;
        }

        @Override
        public boolean isQuietEnabled() {
            return false;
        }

        @Override
        public void quiet(String message) {}

        @Override
        public void quiet(String message, Object... objects) {}

        @Override
        public void quiet(String message, Throwable throwable) {}

        @Override
        public boolean isEnabled(LogLevel level) {
            return true;
        }

        @Override
        public void log(LogLevel level, String message) {}

        @Override
        public void log(LogLevel level, String message, Object... objects) {}

        @Override
        public void log(LogLevel level, String message, Throwable throwable) {}

        // SLF4J Logger methods
        @Override
        public String getName() {
            return "CapturingLogger";
        }

        @Override
        public boolean isTraceEnabled() {
            return false;
        }

        @Override
        public void trace(String msg) {}

        @Override
        public void trace(String format, Object arg) {}

        @Override
        public void trace(String format, Object arg1, Object arg2) {}

        @Override
        public void trace(String format, Object... arguments) {}

        @Override
        public void trace(String msg, Throwable t) {}

        @Override
        public boolean isTraceEnabled(Marker marker) {
            return false;
        }

        @Override
        public void trace(Marker marker, String msg) {}

        @Override
        public void trace(Marker marker, String format, Object arg) {}

        @Override
        public void trace(Marker marker, String format, Object arg1, Object arg2) {}

        @Override
        public void trace(Marker marker, String format, Object... argArray) {}

        @Override
        public void trace(Marker marker, String msg, Throwable t) {}

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void debug(String msg) {}

        @Override
        public void debug(String format, Object arg) {}

        @Override
        public void debug(String format, Object arg1, Object arg2) {}

        @Override
        public void debug(String format, Object... arguments) {}

        @Override
        public void debug(String msg, Throwable t) {}

        @Override
        public boolean isDebugEnabled(Marker marker) {
            return false;
        }

        @Override
        public void debug(Marker marker, String msg) {}

        @Override
        public void debug(Marker marker, String format, Object arg) {}

        @Override
        public void debug(Marker marker, String format, Object arg1, Object arg2) {}

        @Override
        public void debug(Marker marker, String format, Object... arguments) {}

        @Override
        public void debug(Marker marker, String msg, Throwable t) {}

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(String msg) {}

        @Override
        public void info(String format, Object arg) {}

        @Override
        public void info(String format, Object arg1, Object arg2) {}

        @Override
        public void info(String format, Object... arguments) {}

        @Override
        public void info(String msg, Throwable t) {}

        @Override
        public boolean isInfoEnabled(Marker marker) {
            return true;
        }

        @Override
        public void info(Marker marker, String msg) {}

        @Override
        public void info(Marker marker, String format, Object arg) {}

        @Override
        public void info(Marker marker, String format, Object arg1, Object arg2) {}

        @Override
        public void info(Marker marker, String format, Object... arguments) {}

        @Override
        public void info(Marker marker, String msg, Throwable t) {}

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(String msg) {}

        @Override
        public void warn(String format, Object arg) {}

        @Override
        public void warn(String format, Object... arguments) {}

        @Override
        public void warn(String format, Object arg1, Object arg2) {}

        @Override
        public void warn(String msg, Throwable t) {}

        @Override
        public boolean isWarnEnabled(Marker marker) {
            return true;
        }

        @Override
        public void warn(Marker marker, String msg) {}

        @Override
        public void warn(Marker marker, String format, Object arg) {}

        @Override
        public void warn(Marker marker, String format, Object arg1, Object arg2) {}

        @Override
        public void warn(Marker marker, String format, Object... arguments) {}

        @Override
        public void warn(Marker marker, String msg, Throwable t) {}

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public boolean isErrorEnabled(Marker marker) {
            return true;
        }

        @Override
        public void error(Marker marker, String msg) {}

        @Override
        public void error(Marker marker, String format, Object arg) {}

        @Override
        public void error(Marker marker, String format, Object arg1, Object arg2) {}

        @Override
        public void error(Marker marker, String format, Object... arguments) {}

        @Override
        public void error(Marker marker, String msg, Throwable t) {}
    }
}
