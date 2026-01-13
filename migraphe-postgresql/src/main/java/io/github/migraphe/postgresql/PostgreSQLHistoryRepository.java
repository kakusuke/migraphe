package io.github.migraphe.postgresql;

import io.github.migraphe.api.environment.EnvironmentId;
import io.github.migraphe.api.graph.NodeId;
import io.github.migraphe.api.history.ExecutionRecord;
import io.github.migraphe.api.history.ExecutionStatus;
import io.github.migraphe.api.history.HistoryRepository;
import io.github.migraphe.api.task.ExecutionDirection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** PostgreSQL でマイグレーション履歴を永続化する実装。 */
public final class PostgreSQLHistoryRepository implements HistoryRepository {

    private static final String SCHEMA_RESOURCE =
            "/io/github/migraphe/postgresql/schema/init_history_table.sql";

    private final PostgreSQLEnvironment environment;

    public PostgreSQLHistoryRepository(PostgreSQLEnvironment environment) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
    }

    @Override
    public void initialize() {
        try (Connection conn = environment.createConnection();
                Statement stmt = conn.createStatement()) {
            String schemaSql = loadSchemaResource();
            stmt.execute(schemaSql);
        } catch (SQLException e) {
            throw new PostgreSQLException("Failed to initialize history schema", e);
        } catch (IOException e) {
            throw new PostgreSQLException("Failed to load schema resource", e);
        }
    }

    @Override
    public void record(ExecutionRecord record) {
        Objects.requireNonNull(record, "record must not be null");

        String sql =
                """
                INSERT INTO migraphe_history (
                    id, node_id, environment_id, direction, status,
                    executed_at, description, serialized_down_task, duration_ms, error_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = environment.createConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, record.id());
            pstmt.setString(2, record.nodeId().value());
            pstmt.setString(3, record.environmentId().value());
            pstmt.setString(4, record.direction().name());
            pstmt.setString(5, record.status().name());
            pstmt.setTimestamp(6, Timestamp.from(record.executedAt()));
            pstmt.setString(7, record.description());
            pstmt.setString(8, record.serializedDownTask());
            pstmt.setLong(9, record.durationMs());
            pstmt.setString(10, record.errorMessage());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new PostgreSQLException("Failed to record execution history", e);
        }
    }

    @Override
    public boolean wasExecuted(NodeId nodeId, EnvironmentId environmentId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        String sql =
                """
                SELECT COUNT(*) FROM migraphe_history
                WHERE node_id = ? AND environment_id = ? AND status = 'SUCCESS'
                """;

        try (Connection conn = environment.createConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, nodeId.value());
            pstmt.setString(2, environmentId.value());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
        } catch (SQLException e) {
            throw new PostgreSQLException("Failed to check execution status", e);
        }
    }

    @Override
    public List<NodeId> executedNodes(EnvironmentId environmentId) {
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        String sql =
                """
                SELECT DISTINCT node_id FROM migraphe_history
                WHERE environment_id = ? AND status = 'SUCCESS'
                ORDER BY node_id
                """;

        try (Connection conn = environment.createConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, environmentId.value());

            try (ResultSet rs = pstmt.executeQuery()) {
                List<NodeId> nodes = new ArrayList<>();
                while (rs.next()) {
                    nodes.add(NodeId.of(rs.getString("node_id")));
                }
                return nodes;
            }
        } catch (SQLException e) {
            throw new PostgreSQLException("Failed to get executed nodes", e);
        }
    }

    @Override
    public @Nullable ExecutionRecord findLatestRecord(NodeId nodeId, EnvironmentId environmentId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        String sql =
                """
                SELECT * FROM migraphe_history
                WHERE node_id = ? AND environment_id = ?
                ORDER BY executed_at DESC
                LIMIT 1
                """;

        try (Connection conn = environment.createConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, nodeId.value());
            pstmt.setString(2, environmentId.value());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapToExecutionRecord(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new PostgreSQLException("Failed to find latest record", e);
        }
    }

    @Override
    public List<ExecutionRecord> allRecords(EnvironmentId environmentId) {
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        String sql =
                """
                SELECT * FROM migraphe_history
                WHERE environment_id = ?
                ORDER BY executed_at
                """;

        try (Connection conn = environment.createConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, environmentId.value());

            try (ResultSet rs = pstmt.executeQuery()) {
                List<ExecutionRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(mapToExecutionRecord(rs));
                }
                return records;
            }
        } catch (SQLException e) {
            throw new PostgreSQLException("Failed to get all records", e);
        }
    }

    private ExecutionRecord mapToExecutionRecord(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        NodeId nodeId = NodeId.of(rs.getString("node_id"));
        EnvironmentId envId = EnvironmentId.of(rs.getString("environment_id"));
        ExecutionDirection direction = ExecutionDirection.valueOf(rs.getString("direction"));
        ExecutionStatus status = ExecutionStatus.valueOf(rs.getString("status"));
        Instant executedAt = rs.getTimestamp("executed_at").toInstant();
        String description = rs.getString("description");
        String serializedDownTask = rs.getString("serialized_down_task");
        long durationMs = rs.getLong("duration_ms");
        String errorMessage = rs.getString("error_message");

        return new ExecutionRecord(
                id,
                nodeId,
                envId,
                direction,
                status,
                executedAt,
                description,
                serializedDownTask,
                durationMs,
                errorMessage);
    }

    private String loadSchemaResource() throws IOException {
        try (InputStream is = getClass().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (is == null) {
                throw new IOException("Schema resource not found: " + SCHEMA_RESOURCE);
            }
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }
}
