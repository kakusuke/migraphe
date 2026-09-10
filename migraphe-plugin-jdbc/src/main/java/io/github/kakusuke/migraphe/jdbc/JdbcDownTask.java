package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * {@link Task} that executes a reverse (DOWN) migration over JDBC to roll back a previously applied
 * change.
 *
 * <p>On {@link #execute()} the task opens a connection from its {@link JdbcTarget}, splits the DOWN
 * SQL into individual statements with the target's {@link JdbcTarget#statementSplitter()}, and runs
 * each statement in order. Like {@link JdbcUpTask} it honours the {@code autocommit} flag: with
 * autocommit each statement is committed immediately; otherwise all statements run in a single
 * transaction committed on success and rolled back on failure. A DOWN task never produces a further
 * rollback, so its {@link TaskResult} carries no serialized down task.
 */
public final class JdbcDownTask implements Task {

    private final JdbcTarget target;
    private final String downSql;
    private final boolean autocommit;

    private JdbcDownTask(JdbcTarget target, String downSql, boolean autocommit) {
        this.target = Objects.requireNonNull(target, "target must not be null");
        this.downSql = Objects.requireNonNull(downSql, "downSql must not be null");
        this.autocommit = autocommit;
        if (downSql.isBlank()) {
            throw new IllegalArgumentException("downSql must not be blank");
        }
    }

    /**
     * Creates a DOWN task.
     *
     * @param target the target whose connection runs the SQL
     * @param downSql the rollback migration SQL; must not be blank
     * @param autocommit {@code true} to run without an enclosing transaction
     * @return a new {@link JdbcDownTask}
     * @throws IllegalArgumentException if {@code downSql} is blank
     */
    public static JdbcDownTask create(JdbcTarget target, String downSql, boolean autocommit) {
        return new JdbcDownTask(target, downSql, autocommit);
    }

    /**
     * Executes the rollback migration.
     *
     * <p>Statements are split and run in order, either in autocommit mode or within a single
     * transaction depending on the configured flag. On failure the transaction is rolled back (in
     * transactional mode) and an error message is returned. Connection-level failures are also
     * reported as an error variant rather than thrown.
     *
     * @return {@link Result#ok} with a {@link TaskResult} describing the run, or {@link Result#err}
     *     with an error message on failure
     */
    @Override
    public Result<TaskResult, String> execute() {
        long startTime = System.currentTimeMillis();

        try (Connection conn = target.createConnection()) {
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
            for (String sql : target.statementSplitter().split(downSql)) {
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
            for (String sql : target.statementSplitter().split(downSql)) {
                stmt.execute(sql);
            }
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
        String label = target.getDbLabel();
        return autocommit ? label + " DOWN migration (autocommit)" : label + " DOWN migration";
    }
}
