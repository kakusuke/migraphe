package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.task.SqlContentProvider;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * {@link Task} that executes a forward (UP) migration over JDBC.
 *
 * <p>On {@link #execute()} the task opens a connection from its {@link JdbcEnvironment}, splits the
 * UP SQL into individual statements with the environment's {@link
 * JdbcEnvironment#statementSplitter()}, and runs each statement in order. Execution honours the
 * {@code autocommit} flag: when enabled each statement is committed immediately; otherwise all
 * statements run in a single transaction that is committed on success and rolled back on failure.
 *
 * <p>The optional {@code downSql} is not executed here; it is carried into the resulting {@link
 * TaskResult} as the serialized rollback so the history layer can later perform a DOWN migration.
 * As a {@link SqlContentProvider}, the task also exposes its UP SQL for inspection and generators.
 */
public final class JdbcUpTask implements Task, SqlContentProvider {

    private final JdbcEnvironment environment;
    private final String upSql;
    private final @Nullable String downSql;
    private final boolean autocommit;

    private JdbcUpTask(
            JdbcEnvironment environment,
            String upSql,
            @Nullable String downSql,
            boolean autocommit) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.upSql = Objects.requireNonNull(upSql, "upSql must not be null");
        this.downSql = downSql;
        this.autocommit = autocommit;

        if (upSql.isBlank()) {
            throw new IllegalArgumentException("upSql must not be blank");
        }
    }

    /**
     * Creates an UP task.
     *
     * @param environment the environment whose connection runs the SQL
     * @param upSql the forward migration SQL; must not be blank
     * @param downSql the rollback SQL to carry into the result, or {@code null} if the task is not
     *     reversible
     * @param autocommit {@code true} to run without an enclosing transaction
     * @return a new {@link JdbcUpTask}
     * @throws IllegalArgumentException if {@code upSql} is blank
     */
    public static JdbcUpTask create(
            JdbcEnvironment environment,
            String upSql,
            @Nullable String downSql,
            boolean autocommit) {
        return new JdbcUpTask(environment, upSql, downSql, autocommit);
    }

    /**
     * Executes the forward migration.
     *
     * <p>Statements are split and run in order, either in autocommit mode or within a single
     * transaction depending on the configured flag. On success the result carries the rollback SQL
     * (when present) as the serialized down task; on failure the transaction is rolled back (in
     * transactional mode) and an error message is returned. Connection-level failures are also
     * reported as an error variant rather than thrown.
     *
     * @return {@link Result#ok} with a {@link TaskResult} describing the run, or {@link Result#err}
     *     with an error message on failure
     */
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
            for (String sql : environment.statementSplitter().split(upSql)) {
                stmt.execute(sql);
            }
            long durationMs = System.currentTimeMillis() - startTime;

            if (downSql != null) {
                return Result.ok(
                        TaskResult.withDownTask(
                                "UP migration executed in " + durationMs + "ms (autocommit)",
                                downSql));
            } else {
                return Result.ok(
                        TaskResult.withoutDownTask(
                                "UP migration executed in " + durationMs + "ms (autocommit)"));
            }
        } catch (SQLException e) {
            return Result.err("Failed to execute UP migration: " + e.getMessage());
        }
    }

    private Result<TaskResult, String> executeWithTransaction(Connection conn, long startTime) {
        try (Statement stmt = conn.createStatement()) {
            for (String sql : environment.statementSplitter().split(upSql)) {
                stmt.execute(sql);
            }
            conn.commit();

            long durationMs = System.currentTimeMillis() - startTime;

            if (downSql != null) {
                return Result.ok(
                        TaskResult.withDownTask(
                                "UP migration executed in " + durationMs + "ms", downSql));
            } else {
                return Result.ok(
                        TaskResult.withoutDownTask(
                                "UP migration executed in " + durationMs + "ms"));
            }
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                // rollback failed, ignore
            }
            return Result.err("Failed to execute UP migration: " + e.getMessage());
        }
    }

    @Override
    public String description() {
        String label = environment.getDbLabel();
        return autocommit ? label + " UP migration (autocommit)" : label + " UP migration";
    }

    /**
     * Returns the forward migration SQL this task executes.
     *
     * @return the UP SQL
     */
    public String upSql() {
        return upSql;
    }

    @Override
    public String sqlContent() {
        return upSql;
    }
}
