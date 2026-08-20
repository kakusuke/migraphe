package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.ExecutionStatus;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
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

/**
 * Generic {@link HistoryRepository} that persists migration execution history in a relational
 * database via JDBC.
 *
 * <p>All records are stored in a single {@code migraphe_history} table whose schema is brought up
 * to date by {@link #initialize()} from a SQL resource on the classpath. The resource is a list of
 * {@link SchemaStep}s — each a detection query paired with the statements applying it — so the
 * table can gain columns and indexes over time without any schema-version bookkeeping. Each query
 * opens a short-lived connection from the supplied {@link JdbcEnvironment}, so the repository keeps
 * the migration history in the same database the migrations run against. A node is considered
 * applied only when its most recent record is a successful {@code UP}.
 *
 * <p>"Most recent" orders by {@code executed_at} and then by {@code id}. The identifier decides
 * ties because {@link ExecutionRecord}'s factories mint time-ordered UUIDv7 values, and ties are
 * not hypothetical: MariaDB reports itself as version 5.5.5, so the MySQL driver drops fractional
 * seconds and a rollback immediately followed by a re-apply lands on one timestamp. Ordering by
 * {@code executed_at} alone would then leave the winner to the storage engine — silently reporting
 * a rolled-back node as applied. Keeping {@code executed_at} as the primary key of the ordering
 * leaves rows written by older versions, whose identifiers are random UUIDv4 values, ordered
 * exactly as before.
 *
 * <p>The target column is named {@code target_id}. Releases before 0.6.0 called it {@code
 * environment_id}; {@link #initialize()} renames it in place. It has always held a target id, so
 * {@link ExecutionRecord#environmentId()} maps onto it despite the differing name — the API-side
 * rename is a separate change.
 */
public final class JdbcHistoryRepository implements HistoryRepository {

    private static final String DEFAULT_SCHEMA_RESOURCE =
            "/io/github/kakusuke/migraphe/jdbc/schema/init_history_table.sql";

    private final JdbcEnvironment environment;
    private final String schemaResourcePath;

    /**
     * Creates a repository using the bundled default schema resource.
     *
     * @param environment the environment whose database stores the history
     */
    public JdbcHistoryRepository(JdbcEnvironment environment) {
        this(environment, DEFAULT_SCHEMA_RESOURCE);
    }

    /**
     * Creates a repository with a custom schema-initialization resource.
     *
     * <p>Database-specific subclasses or callers can point this at a dialect-tuned DDL script used
     * by {@link #initialize()}.
     *
     * @param environment the environment whose database stores the history
     * @param schemaResourcePath the classpath path of the SQL resource that creates the history
     *     table
     */
    public JdbcHistoryRepository(JdbcEnvironment environment, String schemaResourcePath) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.schemaResourcePath =
                Objects.requireNonNull(schemaResourcePath, "schemaResourcePath must not be null");
    }

    /**
     * Brings the {@code migraphe_history} table up to date with the configured schema resource.
     *
     * <p>The resource is parsed into {@link SchemaStep}s, each pairing a detection query with the
     * statements that apply it. A step whose detection query returns at least one row is skipped,
     * so calling this repeatedly is safe and no schema-version bookkeeping is needed. Detection
     * queries run before the table exists, so a failing one is reported rather than treated as "not
     * applied": mistaking a permission error for a missing table would turn it into a blind DDL
     * attempt.
     *
     * <p>When applying a step fails, the detection query runs once more. A competing process may
     * have applied the same step in between, in which case the failure is benign and swallowed;
     * otherwise it is reported.
     *
     * @throws JdbcException if the schema resource cannot be loaded, or a step cannot be detected
     *     or applied
     */
    @Override
    public void initialize() {
        List<SchemaStep> steps;
        try {
            steps = SchemaStepParser.parse(loadSchemaResource());
        } catch (IOException e) {
            throw new JdbcException("Failed to load schema resource", e);
        }

        try (Connection conn = environment.createConnection()) {
            for (SchemaStep step : steps) {
                applyStep(conn, step);
            }
        } catch (SQLException e) {
            throw new JdbcException("Failed to initialize history schema", e);
        }
    }

    /**
     * Applies one schema step unless it is already in place.
     *
     * <p>Package-private so the detect/apply/re-detect flow can be exercised directly.
     *
     * @param conn the connection to run the step on
     * @param step the step to apply
     * @throws JdbcException if the step cannot be detected or applied
     */
    void applyStep(Connection conn, SchemaStep step) {
        if (isApplied(conn, step)) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            for (String sql : step.applySql()) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            // A competing process may have applied this step between our detection and our apply.
            JdbcException failure =
                    new JdbcException("Failed to apply schema step '" + step.label() + "'", e);
            boolean applied;
            try {
                applied = isApplied(conn, step);
            } catch (JdbcException recheckFailure) {
                failure.addSuppressed(recheckFailure);
                throw failure;
            }
            if (!applied) {
                throw failure;
            }
        }
    }

    /**
     * Runs a step's detection query.
     *
     * @param conn the connection to run the query on
     * @param step the step to test
     * @return {@code true} if the step is already applied; always {@code false} for an
     *     unconditional step
     * @throws JdbcException if the detection query fails
     */
    private boolean isApplied(Connection conn, SchemaStep step) {
        String checkSql = step.checkSql();
        if (checkSql == null) {
            return false;
        }
        try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
            for (int i = 1; i <= pstmt.getParameterMetaData().getParameterCount(); i++) {
                pstmt.setString(i, currentSchema(conn));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new JdbcException("Failed to detect schema step '" + step.label() + "'", e);
        }
    }

    /**
     * Returns the identifier naming the schema the history table lives in.
     *
     * <p>Detection queries compare this against {@code information_schema}'s {@code table_schema}
     * so that a same-named table elsewhere on the server cannot satisfy them. No expression names
     * the current schema across every dialect, so the value is read from the connection instead:
     * {@link Connection#getSchema()} answers on H2 ({@code PUBLIC}) and PostgreSQL ({@code
     * public}), while MySQL and MariaDB leave it unset and carry the database name as the catalog —
     * which is exactly what their {@code table_schema} holds.
     *
     * @param conn the connection whose schema is being resolved
     * @return the current schema, or the catalog when the driver reports no schema
     * @throws SQLException if the connection cannot report either
     */
    private static @Nullable String currentSchema(Connection conn) throws SQLException {
        String schema = conn.getSchema();
        return schema != null ? schema : conn.getCatalog();
    }

    /**
     * Inserts an execution record into the history table.
     *
     * @param record the execution record to persist
     * @throws NullPointerException if {@code record} is {@code null}
     * @throws JdbcException if the insert fails
     */
    @Override
    public void record(ExecutionRecord record) {
        Objects.requireNonNull(record, "record must not be null");

        String sql =
                """
                INSERT INTO migraphe_history (
                    id, node_id, target_id, direction, status,
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
            throw new JdbcException("Failed to record execution history", e);
        }
    }

    /**
     * Returns whether the node has been successfully applied in the given environment.
     *
     * <p>A node counts as applied only when its most recent record (by {@code executed_at}) is a
     * successful {@code UP}.
     *
     * @param nodeId the node to check
     * @param environmentId the environment to check within
     * @return {@code true} if the latest record is a successful UP, otherwise {@code false}
     * @throws NullPointerException if {@code nodeId} or {@code environmentId} is {@code null}
     * @throws JdbcException if the query fails
     */
    @Override
    public boolean wasExecuted(NodeId nodeId, EnvironmentId environmentId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        String sql =
                """
                SELECT direction, status FROM migraphe_history
                WHERE node_id = ? AND target_id = ?
                ORDER BY executed_at DESC, id DESC
                LIMIT 1
                """;

        try (Connection conn = environment.createConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, nodeId.value());
            pstmt.setString(2, environmentId.value());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String direction = rs.getString("direction");
                    String status = rs.getString("status");
                    return "UP".equals(direction) && "SUCCESS".equals(status);
                }
                return false;
            }
        } catch (SQLException e) {
            throw new JdbcException("Failed to check execution status", e);
        }
    }

    /**
     * Returns the identifiers of all nodes currently applied in the given environment.
     *
     * <p>For each node only its most recent record is considered; a node is included when that
     * latest record is a successful {@code UP}. The result is ordered by node identifier.
     *
     * <p>The query deliberately avoids window functions (a correlated {@code MAX(executed_at)}
     * subquery is used instead) so it also runs on pre-window-function servers such as MariaDB 10.1
     * and earlier. Should several records for the same node share the maximum {@code executed_at},
     * the node counts as applied when any of them is a successful {@code UP}.
     *
     * @param environmentId the environment to query
     * @return the identifiers of nodes whose latest record is a successful UP
     * @throws NullPointerException if {@code environmentId} is {@code null}
     * @throws JdbcException if the query fails
     */
    @Override
    public List<NodeId> executedNodes(EnvironmentId environmentId) {
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        String sql =
                """
                SELECT h.node_id FROM migraphe_history h
                WHERE h.target_id = ?
                  AND h.direction = 'UP' AND h.status = 'SUCCESS'
                  AND h.id = (
                      SELECT h2.id FROM migraphe_history h2
                      WHERE h2.target_id = h.target_id AND h2.node_id = h.node_id
                      ORDER BY h2.executed_at DESC, h2.id DESC
                      LIMIT 1
                  )
                ORDER BY h.node_id
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
            throw new JdbcException("Failed to get executed nodes", e);
        }
    }

    /**
     * Returns the most recent execution record for the node in the given environment.
     *
     * @param nodeId the node to look up
     * @param environmentId the environment to look up within
     * @return the latest {@link ExecutionRecord}, or {@code null} if the node has no history in
     *     this environment
     * @throws NullPointerException if {@code nodeId} or {@code environmentId} is {@code null}
     * @throws JdbcException if the query fails
     */
    @Override
    public @Nullable ExecutionRecord findLatestRecord(NodeId nodeId, EnvironmentId environmentId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        String sql =
                """
                SELECT * FROM migraphe_history
                WHERE node_id = ? AND target_id = ?
                ORDER BY executed_at DESC, id DESC
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
            throw new JdbcException("Failed to find latest record", e);
        }
    }

    /**
     * Returns every execution record for the given environment, oldest first.
     *
     * @param environmentId the environment to query
     * @return all {@link ExecutionRecord}s ordered by {@code executed_at} ascending
     * @throws NullPointerException if {@code environmentId} is {@code null}
     * @throws JdbcException if the query fails
     */
    @Override
    public List<ExecutionRecord> allRecords(EnvironmentId environmentId) {
        Objects.requireNonNull(environmentId, "environmentId must not be null");

        String sql =
                """
                SELECT * FROM migraphe_history
                WHERE target_id = ?
                ORDER BY executed_at, id
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
            throw new JdbcException("Failed to get all records", e);
        }
    }

    private ExecutionRecord mapToExecutionRecord(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        NodeId nodeId = NodeId.of(rs.getString("node_id"));
        EnvironmentId envId = EnvironmentId.of(rs.getString("target_id"));
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
        try (InputStream is = getClass().getResourceAsStream(schemaResourcePath)) {
            if (is == null) {
                throw new IOException("Schema resource not found: " + schemaResourcePath);
            }
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }
}
