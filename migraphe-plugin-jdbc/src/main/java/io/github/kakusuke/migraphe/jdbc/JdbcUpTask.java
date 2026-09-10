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
 * <p>On {@link #execute()} the task opens a connection from its {@link JdbcTarget}, splits the UP
 * SQL into individual statements with the target's {@link JdbcTarget#statementSplitter()}, and runs
 * each statement in order. Execution honours the {@code autocommit} flag: when enabled each
 * statement is committed immediately; otherwise all statements run in a single transaction that is
 * committed on success and rolled back on failure.
 *
 * <p>The optional {@code downSql} is not executed here; it is carried into the resulting {@link
 * TaskResult} as the serialized rollback so the history layer can later perform a DOWN migration.
 * As a {@link SqlContentProvider}, the task also exposes its UP SQL for inspection and generators,
 * anything, for a caller that has to record what this task would do rather than do it.
 */
public final class JdbcUpTask implements Task, SqlContentProvider {

    private final JdbcTarget target;
    private final String upSql;
    private final @Nullable String downSql;
    private final boolean autocommit;
    private final boolean autocommitDown;

    private JdbcUpTask(
            JdbcTarget target,
            String upSql,
            @Nullable String downSql,
            boolean autocommit,
            boolean autocommitDown) {
        this.target = Objects.requireNonNull(target, "target must not be null");
        this.upSql = Objects.requireNonNull(upSql, "upSql must not be null");
        this.downSql = downSql;
        this.autocommit = autocommit;
        this.autocommitDown = autocommitDown;

        if (upSql.isBlank()) {
            throw new IllegalArgumentException("upSql must not be blank");
        }
    }

    /**
     * Creates an UP task.
     *
     * @param target the target whose connection runs the SQL
     * @param upSql the forward migration SQL; must not be blank
     * @param downSql the rollback SQL to carry into the result, or {@code null} if the task is not
     *     reversible
     * @param autocommit {@code true} to run without an enclosing transaction
     * @return a new {@link JdbcUpTask}
     * @throws IllegalArgumentException if {@code upSql} is blank
     */
    public static JdbcUpTask create(
            JdbcTarget target, String upSql, @Nullable String downSql, boolean autocommit) {
        return new JdbcUpTask(target, upSql, downSql, autocommit, autocommit);
    }

    /**
     * Creates an UP task that records the rollback's own transaction mode.
     *
     * @param target the target whose connection runs the SQL
     * @param upSql the forward migration SQL; must not be blank
     * @param downSql the rollback SQL to carry into the result, or {@code null} if the task is not
     *     reversible
     * @param autocommit {@code true} to apply without an enclosing transaction
     * @param autocommitDown {@code true} if the rollback must run without one
     * @return a new {@link JdbcUpTask}
     * @throws IllegalArgumentException if {@code upSql} is blank
     */
    public static JdbcUpTask create(
            JdbcTarget target,
            String upSql,
            @Nullable String downSql,
            boolean autocommit,
            boolean autocommitDown) {
        return new JdbcUpTask(target, upSql, downSql, autocommit, autocommitDown);
    }

    /**
     * The plugin's own record of an execution of this task, or {@code null} when there is no
     * rollback to describe.
     *
     * <p>Written in {@code java.util.Properties} syntax so an operator can read it in the history
     * table. It exists because the recorded rollback SQL alone does not say which transaction mode
     * to replay it in, and a migration whose rollback needs autocommit cannot be undone without it.
     *
     * <p>Derived entirely from the definition, so it is the same value whether or not the task has
     * run.
     */
    public @Nullable String pluginMetadata() {
        return downSql == null ? null : "autocommit.down=" + autocommitDown + "\n";
    }

    /**
     * The rollback SQL this task would record.
     *
     * @return the rollback SQL, or {@code null} if this migration is not reversible
     */
    public @Nullable String serializedDownTask() {
        return downSql;
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
            for (String sql : target.statementSplitter().split(upSql)) {
                stmt.execute(sql);
            }
            long durationMs = System.currentTimeMillis() - startTime;

            if (downSql != null) {
                return Result.ok(
                        TaskResult.withDownTask(
                                "UP migration executed in " + durationMs + "ms (autocommit)",
                                downSql,
                                pluginMetadata()));
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
            for (String sql : target.statementSplitter().split(upSql)) {
                stmt.execute(sql);
            }
            conn.commit();

            long durationMs = System.currentTimeMillis() - startTime;

            if (downSql != null) {
                return Result.ok(
                        TaskResult.withDownTask(
                                "UP migration executed in " + durationMs + "ms",
                                downSql,
                                pluginMetadata()));
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
        String label = target.getDbLabel();
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

    /**
     * The forward SQL and the mode it runs in — not the rollback this task carries.
     *
     * <p>{@code downSql} and its mode are recorded by this task but signed by the DOWN task, so
     * including them here would have them counted twice by whoever composes the two.
     *
     * <p>The two parts are length-prefixed rather than concatenated, because concatenating SQL with
     * a mode marker lets a statement ending in the marker's own text stand in for the mode being
     * set.
     */
    @Override
    public String signature() {
        return SignatureFraming.frame(upSql.strip(), autocommit);
    }
}
