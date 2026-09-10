package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionOrigin;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.ExecutionStatus;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.history.HistoryUpgrade;
import io.github.kakusuke.migraphe.api.history.UpgradeContext;
import io.github.kakusuke.migraphe.api.target.TargetId;
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
 * <p>All records are stored in a single {@code migraphe_history} table. Two SQL resources on the
 * classpath describe it, both lists of {@link SchemaStep}s: one that {@link #initialize()} runs to
 * create it in the shape this version writes, and one that {@link #upgrades()} exposes for the
 * upgrade command to bring a table an older release created up to that shape. Splitting them is
 * what keeps a plain {@code status} from altering a history another deployment is still reading.
 * Each query opens a short-lived connection from the supplied {@link JdbcTarget}, so the repository
 * keeps the migration history in the same database the migrations run against. A node is considered
 * applied when its most recent <strong>successful</strong> record is an {@code UP}; records that
 * failed or were skipped never change the applied state.
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
 * environment_id}; the upgrade resource renames it in place. It has always held a target id, so
 * {@link ExecutionRecord#targetId()} maps onto it despite the differing name — the API-side rename
 * is a separate change.
 */
public final class JdbcHistoryRepository implements HistoryRepository {

    private static final String DEFAULT_SCHEMA_RESOURCE =
            "/io/github/kakusuke/migraphe/jdbc/schema/init_history_table.sql";

    private static final String DEFAULT_UPGRADE_RESOURCE =
            "/io/github/kakusuke/migraphe/jdbc/schema/upgrade_history_table.sql";

    private final JdbcTarget target;
    private final String schemaResourcePath;
    private final @Nullable String upgradeResourcePath;

    /**
     * Creates a repository using the bundled default schema and upgrade resources.
     *
     * @param target the target whose database stores the history
     */
    public JdbcHistoryRepository(JdbcTarget target) {
        this(target, DEFAULT_SCHEMA_RESOURCE, DEFAULT_UPGRADE_RESOURCE);
    }

    /**
     * Creates a repository with a custom creation resource and <strong>no upgrades</strong>.
     *
     * <p>A caller who supplies their own DDL has supplied only the shape to create; nothing here
     * can guess how a table an older version of that DDL created should be carried forward, and
     * pairing a custom creation script with the bundled upgrades would alter a table they do not
     * describe. Use {@link #JdbcHistoryRepository(JdbcTarget, String, String)} to supply both.
     *
     * @param target the target whose database stores the history
     * @param schemaResourcePath the classpath path of the SQL resource that creates the history
     *     table
     */
    public JdbcHistoryRepository(JdbcTarget target, String schemaResourcePath) {
        this(target, schemaResourcePath, null);
    }

    /**
     * Creates a repository with a dialect-tuned creation resource and its upgrades.
     *
     * @param target the target whose database stores the history
     * @param schemaResourcePath the classpath path of the SQL resource that creates the history
     *     table in the shape this version writes
     * @param upgradeResourcePath the classpath path of the SQL resource whose steps carry a table
     *     an older release created up to that shape, or {@code null} when there are none
     */
    public JdbcHistoryRepository(
            JdbcTarget target, String schemaResourcePath, @Nullable String upgradeResourcePath) {
        this.target = Objects.requireNonNull(target, "target must not be null");
        this.schemaResourcePath =
                Objects.requireNonNull(schemaResourcePath, "schemaResourcePath must not be null");
        this.upgradeResourcePath = upgradeResourcePath;
    }

    /**
     * Creates the {@code migraphe_history} table, with every column this version writes.
     *
     * <p><strong>It never alters a table that already exists.</strong> The resource it runs leans
     * on {@code IF NOT EXISTS}, so a history an older release created is left exactly as it is — a
     * history can be shared with a deployment still running that release, and dropping a column out
     * from under it because someone ran {@code status} is not a thing any command should do on its
     * own. Everything that changes an existing table is an {@link
     * io.github.kakusuke.migraphe.api.history.HistoryUpgrade}, applied by the upgrade command.
     *
     * <p>The resource is parsed into {@link SchemaStep}s. Steps here carry no detection query and
     * always run, which is safe because each is idempotent, so calling this repeatedly costs
     * nothing and no schema-version bookkeeping is needed.
     *
     * @throws JdbcException if the schema resource cannot be loaded, or a step cannot be applied
     */
    @Override
    public void initialize() {
        List<SchemaStep> steps;
        try {
            steps = SchemaStepParser.parse(loadSchemaResource());
        } catch (IOException e) {
            throw new JdbcException("Failed to load schema resource", e);
        }

        try (Connection conn = target.createConnection()) {
            for (SchemaStep step : steps) {
                applyStep(conn, step);
            }
        } catch (SQLException e) {
            throw new JdbcException("Failed to initialize history schema", e);
        }
    }

    /**
     * Reports whether {@code migraphe_history} is there.
     *
     * <p>Read from {@code information_schema.tables}, bound to the schema the connection reports,
     * so a same-named table elsewhere on the server does not answer for this one — the same rule
     * the upgrade resources' detection queries follow. The name is compared case-insensitively: H2
     * folds it upward while MySQL and PostgreSQL keep it as written.
     *
     * @return {@code true} when the history table exists in this schema
     * @throws JdbcException if the question cannot be asked
     */
    @Override
    public boolean isInitialized() {
        String sql =
                "SELECT 1 FROM information_schema.tables"
                        + " WHERE table_schema = ? AND UPPER(table_name) = 'MIGRAPHE_HISTORY'";
        try (Connection conn = target.createConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, currentSchema(conn));
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new JdbcException("Failed to check whether the history table exists", e);
        }
    }

    /**
     * Returns one upgrade per step of the configured upgrade resource, in the order it lists them.
     *
     * <p>Each step is guarded by its own detection query, so an upgrade reports itself pending only
     * while the change it makes is absent. A step written without a detection query would report
     * pending forever and every command that refuses on a pending upgrade would refuse forever,
     * which is why the bundled upgrade resources guard every step.
     *
     * @return the ordered upgrades, or an empty list when this repository was given no upgrade
     *     resource
     * @throws JdbcException if the upgrade resource cannot be loaded
     */
    @Override
    public List<HistoryUpgrade> upgrades() {
        if (upgradeResourcePath == null) {
            return List.of();
        }
        List<SchemaStep> steps;
        try {
            steps = SchemaStepParser.parse(loadResource(upgradeResourcePath));
        } catch (IOException e) {
            throw new JdbcException("Failed to load upgrade resource", e);
        }
        List<HistoryUpgrade> schemaUpgrades =
                steps.stream().map(step -> (HistoryUpgrade) new SchemaStepUpgrade(step)).toList();
        List<HistoryUpgrade> all = new ArrayList<>(schemaUpgrades);
        all.add(new FillFromDefinitions(schemaUpgrades));
        return List.copyOf(all);
    }

    /**
     * Writes what the definitions can supply into the rows an older release left incomplete.
     *
     * <p>It runs <strong>after</strong> every schema step, because the columns it writes are the
     * ones those steps add. Its pending-ness is theirs: rows need filling exactly when the columns
     * are only now arriving, which is a statement that terminates. Asking instead whether any row
     * still carries a null fingerprint would never stop being true — a row whose task file is gone
     * has no source for one, and every command would refuse forever over a row {@code amend <id>}
     * is there to withdraw.
     *
     * <p><strong>What filling asserts.</strong> A row written before the fingerprint column says
     * that a migration was applied and nothing about its content. Writing today's token onto it
     * asserts that the database matches today's definition. Nothing here can verify that — the tool
     * cannot read the database — so it is the assumption an operator makes by running the upgrade.
     * The cost of it being wrong is a genuinely edited migration reading as unchanged, once. It is
     * confined to columns that are <em>absent</em>: a token that is already recorded and differs is
     * drift, and overwriting that is {@code amend}'s decision to make, not this one's.
     */
    private final class FillFromDefinitions implements HistoryUpgrade {

        private final List<HistoryUpgrade> schemaUpgrades;

        FillFromDefinitions(List<HistoryUpgrade> schemaUpgrades) {
            this.schemaUpgrades = schemaUpgrades;
        }

        @Override
        public String description() {
            return "fill what the definitions still declare";
        }

        @Override
        public boolean isPending() {
            return schemaUpgrades.stream().anyMatch(HistoryUpgrade::isPending);
        }

        @Override
        public void apply(UpgradeContext context) {
            String sql =
                    "UPDATE migraphe_history SET"
                            + " fingerprint = COALESCE(fingerprint, ?),"
                            + " dependencies = COALESCE(dependencies, ?),"
                            + " no_way_back = COALESCE(no_way_back, ?)"
                            + " WHERE id = ?";
            try (Connection conn = target.createConnection();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (ExecutionRecord row : latestApplies()) {
                    MigrationNode node = context.definitions().getNode(row.nodeId()).orElse(null);
                    if (node == null) {
                        // The definitions no longer declare it, so there is nothing to fill it
                        // from. Withdrawing such a row is what naming it in amend does.
                        continue;
                    }
                    pstmt.setString(1, node.fingerprint(context.fingerprinterFor(node.id())));
                    pstmt.setString(2, encodeDependencies(declaredDependencies(node)));
                    pstmt.setString(3, node.noWayBack());
                    pstmt.setString(4, row.id());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            } catch (SQLException e) {
                throw new JdbcException("Failed to fill the history from the definitions", e);
            }
        }

        /** The declared edges, ordered the way an apply records them. */
        private List<NodeId> declaredDependencies(MigrationNode node) {
            return node.dependencies().stream()
                    .sorted(Comparator.comparing(NodeId::value))
                    .toList();
        }
    }

    /** One step of the upgrade resource, presented as the upgrade the command applies. */
    private final class SchemaStepUpgrade implements HistoryUpgrade {

        private final SchemaStep step;

        SchemaStepUpgrade(SchemaStep step) {
            this.step = step;
        }

        @Override
        public String description() {
            return step.label();
        }

        @Override
        public boolean isPending() {
            try (Connection conn = target.createConnection()) {
                return !isApplied(conn, step);
            } catch (SQLException e) {
                throw new JdbcException("Failed to detect schema step '" + step.label() + "'", e);
            }
        }

        /** The context is unused: this step changes the schema, not the rows. */
        @Override
        public void apply(UpgradeContext context) {
            try (Connection conn = target.createConnection()) {
                applyStep(conn, step);
            } catch (SQLException e) {
                throw new JdbcException("Failed to apply schema step '" + step.label() + "'", e);
            }
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
                    executed_at, description, serialized_down_task, duration_ms, error_message,
                    fingerprint, plugin_metadata, dependencies, origin, no_way_back
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = target.createConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, record.id());
            pstmt.setString(2, record.nodeId().value());
            pstmt.setString(3, record.targetId().value());
            pstmt.setString(4, record.direction().name());
            pstmt.setString(5, record.status().name());
            pstmt.setTimestamp(6, Timestamp.from(record.executedAt()));
            pstmt.setString(7, record.description());
            pstmt.setString(8, record.serializedDownTask());
            pstmt.setLong(9, record.durationMs());
            pstmt.setString(10, record.errorMessage());
            pstmt.setString(11, record.fingerprint());
            pstmt.setString(12, record.pluginMetadata());
            pstmt.setString(13, encodeDependencies(record.dependencies()));
            pstmt.setString(14, record.origin().name());
            pstmt.setString(15, record.noWayBack());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new JdbcException("Failed to record execution history", e);
        }
    }

    /**
     * Returns whether the migration is currently applied.
     *
     * <p>Decided by its most recent {@code SUCCESS} row: applied when that is an {@code UP}, not
     * applied when it is a {@code DOWN}, and not applied when there is no successful row. A
     * rollback that failed leaves it applied. The query names no target — an identifier is unique
     * across the project, so every row for it is a row about it.
     *
     * @param nodeId the migration to check
     * @return {@code true} if the migration is currently applied, otherwise {@code false}
     * @throws NullPointerException if {@code nodeId} is {@code null}
     * @throws JdbcException if the query fails
     */
    @Override
    public boolean wasExecuted(NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        String sql =
                """
                SELECT direction FROM migraphe_history
                WHERE node_id = ? AND status = 'SUCCESS'
                ORDER BY executed_at DESC, id DESC
                LIMIT 1
                """;

        try (Connection conn = target.createConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, nodeId.value());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return "UP".equals(rs.getString("direction"));
                }
                return false;
            }
        } catch (SQLException e) {
            throw new JdbcException("Failed to check execution status", e);
        }
    }

    /**
     * Returns the identifiers of all nodes currently applied in the given target.
     *
     * <p>For each node only its most recent {@code SUCCESS} record is considered; the node is
     * included when that record is an {@code UP}. This is the set form of {@link #wasExecuted} and
     * agrees with it for every node. The result is ordered by node identifier.
     *
     * <p>The query deliberately avoids window functions (a correlated subquery selecting the latest
     * successful {@code id} is used instead) so it also runs on pre-window-function servers such as
     * MariaDB 10.1 and earlier. Ties on {@code executed_at} are broken by {@code id}, so exactly
     * one record is selected per node.
     *
     * @return the identifiers of the currently applied migrations
     * @throws JdbcException if the query fails
     */
    @Override
    public List<NodeId> executedNodes() {
        String sql =
                """
                SELECT h.node_id FROM migraphe_history h
                WHERE h.direction = 'UP' AND h.status = 'SUCCESS'
                  AND h.id = (
                      SELECT h2.id FROM migraphe_history h2
                      WHERE h2.node_id = h.node_id AND h2.status = 'SUCCESS'
                      ORDER BY h2.executed_at DESC, h2.id DESC
                      LIMIT 1
                  )
                ORDER BY h.node_id
                """;

        try (Connection conn = target.createConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

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
     * Returns the most recent execution record for the node, whatever target wrote it.
     *
     * @param nodeId the node to look up
     * @return the latest {@link ExecutionRecord}, or {@code null} if the node has no history
     * @throws NullPointerException if {@code nodeId} is {@code null}
     * @throws JdbcException if the query fails
     */
    @Override
    public @Nullable ExecutionRecord findLatestRecord(NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        String sql =
                """
                SELECT * FROM migraphe_history
                WHERE node_id = ?
                ORDER BY executed_at DESC, id DESC
                LIMIT 1
                """;

        try (Connection conn = target.createConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, nodeId.value());

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
     * Returns every execution record in the history table, oldest first.
     *
     * <p>One table holds every target's rows — {@code history.target} names the one connection the
     * history lives on — so this needs no predicate. The rows carry their own {@code target_id} and
     * the caller filters.
     *
     * @return all {@link ExecutionRecord}s ordered by {@code executed_at} ascending
     * @throws JdbcException if the query fails
     */
    @Override
    public List<ExecutionRecord> allRecords() {
        String sql =
                """
                SELECT * FROM migraphe_history
                ORDER BY executed_at, id
                """;

        try (Connection conn = target.createConnection();
                Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery(sql)) {
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
        TargetId targetId = TargetId.of(rs.getString("target_id"));
        ExecutionDirection direction = ExecutionDirection.valueOf(rs.getString("direction"));
        ExecutionStatus status = ExecutionStatus.valueOf(rs.getString("status"));
        Instant executedAt = rs.getTimestamp("executed_at").toInstant();
        String description = rs.getString("description");
        String serializedDownTask = rs.getString("serialized_down_task");
        long durationMs = rs.getLong("duration_ms");
        String errorMessage = rs.getString("error_message");
        String fingerprint = rs.getString("fingerprint");
        String pluginMetadata = rs.getString("plugin_metadata");
        List<NodeId> dependencies = decodeDependencies(rs.getString("dependencies"));
        ExecutionOrigin origin = decodeOrigin(rs.getString("origin"));
        String noWayBack = rs.getString("no_way_back");

        return new ExecutionRecord(
                id,
                nodeId,
                targetId,
                direction,
                status,
                executedAt,
                description,
                serializedDownTask,
                durationMs,
                errorMessage,
                fingerprint,
                pluginMetadata,
                dependencies,
                origin,
                noWayBack);
    }

    /**
     * Reads the {@code origin} column, treating an absent value as {@link
     * ExecutionOrigin#EXECUTED}.
     *
     * <p>Every version that could write a row before this column existed wrote it by running
     * something, so there is no third state to represent.
     */
    private static ExecutionOrigin decodeOrigin(@Nullable String stored) {
        return stored == null ? ExecutionOrigin.EXECUTED : ExecutionOrigin.valueOf(stored);
    }

    /**
     * Encodes recorded dependencies for the {@code dependencies} column, or {@code null} to leave
     * it unrecorded.
     *
     * <p>Newline-separated because a node id is derived from a file path and so cannot contain one.
     * An empty list encodes as the empty string rather than {@code null}: the column has to keep
     * "stood on nothing" apart from "nobody wrote it down".
     */
    private static @Nullable String encodeDependencies(@Nullable List<NodeId> dependencies) {
        if (dependencies == null) {
            return null;
        }
        return dependencies.stream().map(NodeId::value).collect(Collectors.joining("\n"));
    }

    /** Reads back what {@link #encodeDependencies} wrote. */
    private static @Nullable List<NodeId> decodeDependencies(@Nullable String encoded) {
        if (encoded == null) {
            return null;
        }
        if (encoded.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(encoded.split("\n", -1)).map(NodeId::of).toList();
    }

    private String loadSchemaResource() throws IOException {
        return loadResource(schemaResourcePath);
    }

    private String loadResource(String path) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Schema resource not found: " + path);
            }
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }
}
