package io.github.migraphe.postgresql;

import io.github.migraphe.core.common.Result;
import io.github.migraphe.core.task.Task;
import io.github.migraphe.core.task.TaskResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL で UP マイグレーション（前進）を実行するタスク。 */
public final class PostgreSQLUpTask implements Task {

    private final PostgreSQLEnvironment environment;
    private final String upSql;
    private final Optional<String> downSql;

    private PostgreSQLUpTask(
            PostgreSQLEnvironment environment, String upSql, Optional<String> downSql) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.upSql = Objects.requireNonNull(upSql, "upSql must not be null");
        this.downSql = Objects.requireNonNull(downSql, "downSql must not be null");

        if (upSql.isBlank()) {
            throw new IllegalArgumentException("upSql must not be blank");
        }
    }

    /**
     * UP SQL と DOWN SQL から UP タスクを作成する。
     *
     * @param environment PostgreSQL 環境
     * @param upSql UP SQL
     * @param downSql DOWN SQL（ロールバック用、Optional）
     * @return UP タスク
     */
    public static PostgreSQLUpTask create(
            PostgreSQLEnvironment environment, String upSql, Optional<String> downSql) {
        return new PostgreSQLUpTask(environment, upSql, downSql);
    }

    @Override
    public Result<TaskResult, String> execute() {
        long startTime = System.currentTimeMillis();

        try (Connection conn = environment.createConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(upSql);
                conn.commit();

                long durationMs = System.currentTimeMillis() - startTime;

                // DOWN SQL があれば、そのまま（生 SQL テキスト）をシリアライズする
                if (downSql.isPresent()) {
                    return Result.ok(
                            TaskResult.withDownTask(
                                    "UP migration executed in " + durationMs + "ms",
                                    downSql.get()));
                } else {
                    return Result.ok(
                            TaskResult.withoutDownTask(
                                    "UP migration executed in " + durationMs + "ms"));
                }
            } catch (SQLException e) {
                conn.rollback();
                return Result.err("Failed to execute UP migration: " + e.getMessage());
            }
        } catch (SQLException e) {
            return Result.err("Failed to establish database connection: " + e.getMessage());
        }
    }

    @Override
    public String description() {
        return "PostgreSQL UP migration";
    }
}
