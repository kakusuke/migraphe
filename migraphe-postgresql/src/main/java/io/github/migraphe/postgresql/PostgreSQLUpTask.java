package io.github.migraphe.postgresql;

import io.github.migraphe.api.common.Result;
import io.github.migraphe.api.task.Task;
import io.github.migraphe.api.task.TaskResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** PostgreSQL で UP マイグレーション（前進）を実行するタスク。 */
public final class PostgreSQLUpTask implements Task {

    private final PostgreSQLEnvironment environment;
    private final String upSql;
    private final @Nullable String downSql;
    private final boolean autocommit;

    private PostgreSQLUpTask(
            PostgreSQLEnvironment environment,
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
     * UP SQL と DOWN SQL から UP タスクを作成する。
     *
     * @param environment PostgreSQL 環境
     * @param upSql UP SQL
     * @param downSql DOWN SQL（ロールバック用、null 許容）
     * @param autocommit autocommit モードで実行するかどうか
     * @return UP タスク
     */
    public static PostgreSQLUpTask create(
            PostgreSQLEnvironment environment,
            String upSql,
            @Nullable String downSql,
            boolean autocommit) {
        return new PostgreSQLUpTask(environment, upSql, downSql, autocommit);
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
            // autocommit モードでは各ステートメントを個別に実行
            // （CREATE DATABASE などは暗黙的トランザクションでも実行不可のため）
            for (String sql : splitStatements(upSql)) {
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

    /**
     * SQL テキストをステートメントに分割する。
     *
     * <p>セミコロン + 空白/コメント + 改行 のパターンで分割。 文字列リテラル内のセミコロンで誤分割しないよう、行末のみを対象とする。
     */
    private static String[] splitStatements(String sql) {
        // セミコロン + 空白/コメント(optional) + 改行 で分割
        String[] parts = sql.split(";\\s*?(--[^\\n]*)?\\r?\\n");
        return java.util.Arrays.stream(parts)
                .map(String::trim)
                .map(s -> s.endsWith(";") ? s.substring(0, s.length() - 1).trim() : s)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    private Result<TaskResult, String> executeWithTransaction(Connection conn, long startTime) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(upSql);
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
        return autocommit ? "PostgreSQL UP migration (autocommit)" : "PostgreSQL UP migration";
    }
}
