package io.github.migraphe.postgresql;

import io.github.migraphe.api.common.Result;
import io.github.migraphe.api.task.Task;
import io.github.migraphe.api.task.TaskResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/** PostgreSQL で DOWN マイグレーション（ロールバック）を実行するタスク。 */
public final class PostgreSQLDownTask implements Task {

    private final PostgreSQLEnvironment environment;
    private final String downSql;
    private final boolean autocommit;

    private PostgreSQLDownTask(
            PostgreSQLEnvironment environment, String downSql, boolean autocommit) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.downSql = Objects.requireNonNull(downSql, "downSql must not be null");
        this.autocommit = autocommit;
        if (downSql.isBlank()) {
            throw new IllegalArgumentException("downSql must not be blank");
        }
    }

    /**
     * DOWN SQL から DOWN タスクを作成する。
     *
     * @param environment PostgreSQL 環境
     * @param downSql DOWN SQL（生 SQL テキスト）
     * @param autocommit autocommit モードで実行するかどうか
     * @return DOWN タスク
     */
    public static PostgreSQLDownTask create(
            PostgreSQLEnvironment environment, String downSql, boolean autocommit) {
        return new PostgreSQLDownTask(environment, downSql, autocommit);
    }

    @Override
    public Result<TaskResult, String> execute() {
        long startTime = System.currentTimeMillis();

        try (Connection conn = environment.createConnection()) {
            if (autocommit) {
                conn.setAutoCommit(true);
                return executeWithAutocommit(conn, startTime);
            } else {
                conn.setAutoCommit(false);
                return executeWithTransaction(conn, startTime);
            }
        } catch (SQLException e) {
            return Result.err("Failed to establish database connection: " + e.getMessage());
        }
    }

    private Result<TaskResult, String> executeWithAutocommit(Connection conn, long startTime) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(downSql);
            long durationMs = System.currentTimeMillis() - startTime;
            return Result.ok(
                    TaskResult.withoutDownTask(
                            "DOWN migration executed in " + durationMs + "ms (autocommit)"));
        } catch (SQLException e) {
            return Result.err("Failed to execute DOWN migration: " + e.getMessage());
        }
    }

    private Result<TaskResult, String> executeWithTransaction(Connection conn, long startTime) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(downSql);
            conn.commit();

            long durationMs = System.currentTimeMillis() - startTime;
            return Result.ok(
                    TaskResult.withoutDownTask("DOWN migration executed in " + durationMs + "ms"));
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                // rollback failed, ignore
            }
            return Result.err("Failed to execute DOWN migration: " + e.getMessage());
        }
    }

    @Override
    public String description() {
        return autocommit ? "PostgreSQL DOWN migration (autocommit)" : "PostgreSQL DOWN migration";
    }
}
