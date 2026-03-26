package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/** JDBC で DOWN マイグレーション（ロールバック）を実行するタスク。 */
public final class JdbcDownTask implements Task {

    private final JdbcEnvironment environment;
    private final String downSql;
    private final boolean autocommit;

    private JdbcDownTask(JdbcEnvironment environment, String downSql, boolean autocommit) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.downSql = Objects.requireNonNull(downSql, "downSql must not be null");
        this.autocommit = autocommit;
        if (downSql.isBlank()) {
            throw new IllegalArgumentException("downSql must not be blank");
        }
    }

    public static JdbcDownTask create(
            JdbcEnvironment environment, String downSql, boolean autocommit) {
        return new JdbcDownTask(environment, downSql, autocommit);
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
            for (String sql : SqlStatements.splitStatements(downSql)) {
                stmt.execute(sql);
            }
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
        String label = environment.getDbLabel();
        return autocommit ? label + " DOWN migration (autocommit)" : label + " DOWN migration";
    }
}
