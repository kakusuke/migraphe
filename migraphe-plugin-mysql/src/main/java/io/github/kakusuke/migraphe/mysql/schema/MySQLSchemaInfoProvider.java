package io.github.kakusuke.migraphe.mysql.schema;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.schema.SchemaInfoProvider;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcColumnInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcForeignKeyInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcIndexColumn;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcIndexInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcPrimaryKeyInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaDetail;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcTableInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcViewInfo;
import io.github.kakusuke.migraphe.mysql.MySQLEnvironment;
import io.github.kakusuke.migraphe.mysql.MySQLException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Extracts a {@link MySQLSchemaInfo} snapshot from a connected MySQL environment.
 *
 * <p>This is the MySQL implementation of {@link SchemaInfoProvider}. It opens a connection through
 * the supplied {@link MySQLEnvironment}, reads the portable structure (tables, views, columns,
 * primary/foreign keys, indexes) through {@link DatabaseMetaData}, and supplements it with
 * MySQL-only objects queried directly from {@code information_schema}: storage engines, table
 * metadata, triggers, stored routines, scheduled events, table partitions, and view definers. The
 * combined result is the typed data object consumed by the {@code mysql-schema} generator source
 * (see {@link MySQLSchemaSourcePlugin}).
 *
 * <p>All extraction is scoped to the connection's current catalog (the active database), so the
 * returned snapshot describes a single MySQL database.
 *
 * @see MySQLSchemaSourcePlugin
 * @see MySQLSchemaInfo
 */
public class MySQLSchemaInfoProvider implements SchemaInfoProvider<MySQLSchemaInfo> {

    /** Creates a new {@code MySQLSchemaInfoProvider}. */
    public MySQLSchemaInfoProvider() {}

    /**
     * Connects to the given environment and extracts a complete MySQL schema snapshot.
     *
     * @param environment the environment to introspect; must be a {@link MySQLEnvironment}
     * @return the extracted MySQL schema information for the connection's current database
     * @throws MySQLException if {@code environment} is not a {@link MySQLEnvironment}, or if a
     *     {@link SQLException} occurs while reading metadata or {@code information_schema}
     */
    @Override
    public MySQLSchemaInfo getSchemaInfo(Environment environment) {
        if (!(environment instanceof MySQLEnvironment mysqlEnv)) {
            throw new MySQLException(
                    "Environment must be a MySQLEnvironment: " + environment.getClass().getName());
        }
        try (Connection conn = mysqlEnv.createConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            JdbcSchemaDetail schemaDetail = buildSchemaDetail(meta, catalog);
            return new MySQLSchemaInfo(
                    List.of(schemaDetail),
                    List.copyOf(extractStorageEngines(conn)),
                    List.copyOf(extractTableMeta(conn, catalog)),
                    List.copyOf(extractTriggers(conn, catalog)),
                    List.copyOf(extractRoutines(conn, catalog)),
                    List.copyOf(extractEvents(conn, catalog)),
                    List.copyOf(extractPartitions(conn, catalog)),
                    Map.copyOf(extractViewDefiners(conn, catalog)));
        } catch (SQLException e) {
            throw new MySQLException("Failed to retrieve schema info", e);
        }
    }

    private List<MySQLStorageEngineInfo> extractStorageEngines(Connection conn)
            throws SQLException {
        List<MySQLStorageEngineInfo> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT ENGINE, SUPPORT, TRANSACTIONS, XA, SAVEPOINTS"
                                        + " FROM information_schema.ENGINES")) {
            while (rs.next()) {
                result.add(
                        new MySQLStorageEngineInfo(
                                rs.getString("ENGINE"),
                                rs.getString("SUPPORT"),
                                nullToEmpty(rs.getString("TRANSACTIONS")),
                                nullToEmpty(rs.getString("XA")),
                                nullToEmpty(rs.getString("SAVEPOINTS"))));
            }
        }
        return result;
    }

    private List<MySQLTableMetaInfo> extractTableMeta(Connection conn, String catalog)
            throws SQLException {
        List<MySQLTableMetaInfo> result = new ArrayList<>();
        String sql =
                "SELECT TABLE_SCHEMA, TABLE_NAME, ENGINE, TABLE_COLLATION, ROW_FORMAT,"
                        + " TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND"
                        + " TABLE_TYPE = 'BASE TABLE'";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, catalog);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(
                            new MySQLTableMetaInfo(
                                    rs.getString("TABLE_SCHEMA"),
                                    rs.getString("TABLE_NAME"),
                                    nullToEmpty(rs.getString("ENGINE")),
                                    nullToEmpty(rs.getString("TABLE_COLLATION")),
                                    nullToEmpty(rs.getString("ROW_FORMAT")),
                                    rs.getString("TABLE_COMMENT")));
                }
            }
        }
        return result;
    }

    private List<MySQLTriggerInfo> extractTriggers(Connection conn, String catalog)
            throws SQLException {
        List<MySQLTriggerInfo> result = new ArrayList<>();
        String sql =
                "SELECT TRIGGER_SCHEMA, EVENT_OBJECT_TABLE, TRIGGER_NAME,"
                        + " ACTION_TIMING, EVENT_MANIPULATION, ACTION_STATEMENT, DEFINER"
                        + " FROM information_schema.TRIGGERS"
                        + " WHERE TRIGGER_SCHEMA = ?";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, catalog);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(
                            new MySQLTriggerInfo(
                                    rs.getString("TRIGGER_SCHEMA"),
                                    rs.getString("EVENT_OBJECT_TABLE"),
                                    rs.getString("TRIGGER_NAME"),
                                    rs.getString("ACTION_TIMING"),
                                    rs.getString("EVENT_MANIPULATION"),
                                    rs.getString("ACTION_STATEMENT"),
                                    rs.getString("DEFINER")));
                }
            }
        }
        return result;
    }

    private List<MySQLRoutineInfo> extractRoutines(Connection conn, String catalog)
            throws SQLException {
        Map<RoutineKey, List<MySQLParameterInfo>> parameters =
                extractRoutineParameters(conn, catalog);
        List<MySQLRoutineInfo> result = new ArrayList<>();
        String sql =
                "SELECT ROUTINE_SCHEMA, ROUTINE_NAME, ROUTINE_TYPE,"
                        + " DTD_IDENTIFIER, SECURITY_TYPE, DEFINER, ROUTINE_DEFINITION"
                        + " FROM information_schema.ROUTINES"
                        + " WHERE ROUTINE_SCHEMA = ?";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, catalog);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String schema = rs.getString("ROUTINE_SCHEMA");
                    String name = rs.getString("ROUTINE_NAME");
                    String type = rs.getString("ROUTINE_TYPE");
                    result.add(
                            new MySQLRoutineInfo(
                                    schema,
                                    name,
                                    type,
                                    nullToEmpty(rs.getString("DTD_IDENTIFIER")),
                                    List.copyOf(
                                            parameters.getOrDefault(
                                                    new RoutineKey(schema, name, type), List.of())),
                                    rs.getString("SECURITY_TYPE"),
                                    rs.getString("DEFINER"),
                                    rs.getString("ROUTINE_DEFINITION")));
                }
            }
        }
        return result;
    }

    /**
     * Reads the declared parameters of every routine in the schema, grouped by routine identity.
     *
     * <p>The grouping key includes {@code ROUTINE_TYPE} because a procedure and a function may
     * share a name, in which case keying on the name alone would merge their parameter lists. A
     * function's return value, which the source table reports at ordinal position {@code 0} with no
     * mode and no name, is excluded by the query.
     *
     * @param conn the open connection to query
     * @param catalog the schema (MySQL database) to inspect
     * @return the parameters of each routine, in ordinal order
     * @throws SQLException if the query fails
     */
    private Map<RoutineKey, List<MySQLParameterInfo>> extractRoutineParameters(
            Connection conn, String catalog) throws SQLException {
        Map<RoutineKey, List<MySQLParameterInfo>> result = new LinkedHashMap<>();
        String sql =
                "SELECT SPECIFIC_SCHEMA, SPECIFIC_NAME, ROUTINE_TYPE, ORDINAL_POSITION,"
                        + " PARAMETER_MODE, PARAMETER_NAME, DTD_IDENTIFIER"
                        + " FROM information_schema.PARAMETERS"
                        + " WHERE SPECIFIC_SCHEMA = ? AND ORDINAL_POSITION > 0"
                        + " ORDER BY SPECIFIC_NAME, ROUTINE_TYPE, ORDINAL_POSITION";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, catalog);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RoutineKey key =
                            new RoutineKey(
                                    rs.getString("SPECIFIC_SCHEMA"),
                                    rs.getString("SPECIFIC_NAME"),
                                    rs.getString("ROUTINE_TYPE"));
                    result.computeIfAbsent(key, k -> new ArrayList<>())
                            .add(
                                    new MySQLParameterInfo(
                                            rs.getInt("ORDINAL_POSITION"),
                                            nullToEmpty(rs.getString("PARAMETER_MODE")),
                                            nullToEmpty(rs.getString("PARAMETER_NAME")),
                                            nullToEmpty(rs.getString("DTD_IDENTIFIER"))));
                }
            }
        }
        return result;
    }

    private List<MySQLEventInfo> extractEvents(Connection conn, String catalog)
            throws SQLException {
        List<MySQLEventInfo> result = new ArrayList<>();
        String sql =
                "SELECT EVENT_SCHEMA, EVENT_NAME, EVENT_TYPE,"
                        + " INTERVAL_VALUE, INTERVAL_FIELD, STATUS, EVENT_DEFINITION, DEFINER"
                        + " FROM information_schema.EVENTS"
                        + " WHERE EVENT_SCHEMA = ?";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, catalog);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(
                            new MySQLEventInfo(
                                    rs.getString("EVENT_SCHEMA"),
                                    rs.getString("EVENT_NAME"),
                                    rs.getString("EVENT_TYPE"),
                                    rs.getString("INTERVAL_VALUE"),
                                    rs.getString("INTERVAL_FIELD"),
                                    rs.getString("STATUS"),
                                    rs.getString("EVENT_DEFINITION"),
                                    rs.getString("DEFINER")));
                }
            }
        }
        return result;
    }

    private Map<String, String> extractViewDefiners(Connection conn, String catalog)
            throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        String sql =
                "SELECT TABLE_SCHEMA, TABLE_NAME, DEFINER FROM information_schema.VIEWS"
                        + " WHERE TABLE_SCHEMA = ?";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, catalog);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String definer = rs.getString("DEFINER");
                    if (definer != null) {
                        result.put(
                                rs.getString("TABLE_SCHEMA") + "." + rs.getString("TABLE_NAME"),
                                definer);
                    }
                }
            }
        }
        return result;
    }

    private List<MySQLPartitionInfo> extractPartitions(Connection conn, String catalog)
            throws SQLException {
        List<MySQLPartitionInfo> result = new ArrayList<>();
        String sql =
                "SELECT TABLE_SCHEMA, TABLE_NAME, PARTITION_METHOD, PARTITION_EXPRESSION, COUNT(*)"
                    + " AS partition_count FROM information_schema.PARTITIONS WHERE PARTITION_NAME"
                    + " IS NOT NULL AND TABLE_SCHEMA = ? GROUP BY TABLE_SCHEMA, TABLE_NAME,"
                    + " PARTITION_METHOD, PARTITION_EXPRESSION";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, catalog);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(
                            new MySQLPartitionInfo(
                                    rs.getString("TABLE_SCHEMA"),
                                    rs.getString("TABLE_NAME"),
                                    rs.getString("PARTITION_METHOD"),
                                    rs.getString("PARTITION_EXPRESSION"),
                                    rs.getInt("partition_count")));
                }
            }
        }
        return result;
    }

    private JdbcSchemaDetail buildSchemaDetail(DatabaseMetaData meta, String catalog)
            throws SQLException {
        List<JdbcTableInfo> tables = buildTables(meta, catalog);
        List<JdbcViewInfo> views = buildViews(meta, catalog);
        return new JdbcSchemaDetail(
                catalog, tables, views, List.of(), List.of(), List.of(), List.of());
    }

    private List<JdbcTableInfo> buildTables(DatabaseMetaData meta, String catalog)
            throws SQLException {
        List<JdbcTableInfo> tables = new ArrayList<>();
        try (ResultSet rs = meta.getTables(catalog, null, null, new String[] {"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String remarks = nullToEmpty(rs.getString("REMARKS"));
                List<JdbcColumnInfo> columns = buildColumns(meta, catalog, tableName);
                JdbcPrimaryKeyInfo primaryKey = buildPrimaryKey(meta, catalog, tableName);
                List<JdbcForeignKeyInfo> foreignKeys = buildForeignKeys(meta, catalog, tableName);
                List<JdbcForeignKeyInfo> exportedKeys = buildExportedKeys(meta, catalog, tableName);
                List<JdbcIndexInfo> indexes = buildIndexes(meta, catalog, tableName);
                tables.add(
                        new JdbcTableInfo(
                                tableName,
                                remarks,
                                columns,
                                primaryKey,
                                foreignKeys,
                                exportedKeys,
                                List.of(),
                                indexes,
                                List.of()));
            }
        }
        return tables;
    }

    private List<JdbcViewInfo> buildViews(DatabaseMetaData meta, String catalog)
            throws SQLException {
        List<JdbcViewInfo> views = new ArrayList<>();
        try (ResultSet rs = meta.getTables(catalog, null, null, new String[] {"VIEW"})) {
            while (rs.next()) {
                String viewName = rs.getString("TABLE_NAME");
                String remarks = nullToEmpty(rs.getString("REMARKS"));
                List<JdbcColumnInfo> columns = buildColumns(meta, catalog, viewName);
                views.add(new JdbcViewInfo(viewName, remarks, columns, ""));
            }
        }
        return views;
    }

    private List<JdbcColumnInfo> buildColumns(
            DatabaseMetaData meta, String catalog, String tableName) throws SQLException {
        List<JdbcColumnInfo> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, null, tableName, null)) {
            while (rs.next()) {
                columns.add(
                        new JdbcColumnInfo(
                                rs.getString("COLUMN_NAME"),
                                rs.getString("TYPE_NAME"),
                                rs.getInt("DATA_TYPE"),
                                rs.getInt("COLUMN_SIZE"),
                                rs.getInt("DECIMAL_DIGITS"),
                                "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                                rs.getString("COLUMN_DEF"),
                                "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT")),
                                "YES".equalsIgnoreCase(rs.getString("IS_GENERATEDCOLUMN")),
                                rs.getString("REMARKS"),
                                rs.getInt("ORDINAL_POSITION")));
            }
        }
        return columns;
    }

    private JdbcPrimaryKeyInfo buildPrimaryKey(
            DatabaseMetaData meta, String catalog, String tableName) throws SQLException {
        String pkName = "";
        List<String> columns = new ArrayList<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, null, tableName)) {
            while (rs.next()) {
                String name = rs.getString("PK_NAME");
                if (name != null) {
                    pkName = name;
                }
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return new JdbcPrimaryKeyInfo(pkName, columns);
    }

    private List<JdbcForeignKeyInfo> buildForeignKeys(
            DatabaseMetaData meta, String catalog, String tableName) throws SQLException {
        return buildKeyInfo(meta.getImportedKeys(catalog, null, tableName), true);
    }

    private List<JdbcForeignKeyInfo> buildExportedKeys(
            DatabaseMetaData meta, String catalog, String tableName) throws SQLException {
        return buildKeyInfo(meta.getExportedKeys(catalog, null, tableName), false);
    }

    private List<JdbcForeignKeyInfo> buildKeyInfo(ResultSet rs, boolean imported)
            throws SQLException {
        Map<BuilderKey, ForeignKeyBuilder> builders = new LinkedHashMap<>();
        try (rs) {
            while (rs.next()) {
                String fkName = nullToEmpty(rs.getString("FK_NAME"));
                String fkTableCat = nullToEmpty(rs.getString("FKTABLE_CAT"));
                String fkTableName = nullToEmpty(rs.getString("FKTABLE_NAME"));
                BuilderKey key = new BuilderKey(fkTableCat, fkTableName, fkName);
                ForeignKeyBuilder builder =
                        builders.computeIfAbsent(key, k -> new ForeignKeyBuilder(k.fkName()));
                if (imported) {
                    builder.columns.add(rs.getString("FKCOLUMN_NAME"));
                    builder.referencedSchema = nullToEmpty(rs.getString("PKTABLE_SCHEM"));
                    builder.referencedTable = rs.getString("PKTABLE_NAME");
                    builder.referencedColumns.add(rs.getString("PKCOLUMN_NAME"));
                } else {
                    builder.columns.add(rs.getString("PKCOLUMN_NAME"));
                    builder.referencedSchema = nullToEmpty(rs.getString("FKTABLE_SCHEM"));
                    builder.referencedTable = rs.getString("FKTABLE_NAME");
                    builder.referencedColumns.add(rs.getString("FKCOLUMN_NAME"));
                }
                builder.updateRule = ruleToString(rs.getInt("UPDATE_RULE"));
                builder.deleteRule = ruleToString(rs.getInt("DELETE_RULE"));
            }
        }
        return builders.values().stream().map(ForeignKeyBuilder::build).toList();
    }

    private List<JdbcIndexInfo> buildIndexes(
            DatabaseMetaData meta, String catalog, String tableName) throws SQLException {
        Map<String, IndexBuilder> builders = new LinkedHashMap<>();
        try (ResultSet rs = meta.getIndexInfo(catalog, null, tableName, false, false)) {
            while (rs.next()) {
                short type = rs.getShort("TYPE");
                if (type == DatabaseMetaData.tableIndexStatistic) {
                    continue;
                }
                String indexName = rs.getString("INDEX_NAME");
                if (indexName == null) {
                    continue;
                }
                IndexBuilder builder = builders.computeIfAbsent(indexName, IndexBuilder::new);
                builder.unique = !rs.getBoolean("NON_UNIQUE");
                String columnName = rs.getString("COLUMN_NAME");
                String ascOrDesc = rs.getString("ASC_OR_DESC");
                if (columnName != null) {
                    builder.columns.add(
                            new JdbcIndexColumn(columnName, ascOrDesc != null ? ascOrDesc : "A"));
                }
            }
        }
        return builders.values().stream().map(IndexBuilder::build).toList();
    }

    private static String ruleToString(int rule) {
        return switch (rule) {
            case DatabaseMetaData.importedKeyCascade -> "CASCADE";
            case DatabaseMetaData.importedKeySetNull -> "SET NULL";
            case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
            case DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
            case DatabaseMetaData.importedKeyNoAction -> "NO ACTION";
            default -> "UNKNOWN";
        };
    }

    private static String nullToEmpty(@Nullable String value) {
        return value != null ? value : "";
    }

    private record BuilderKey(String fkTableCat, String fkTableName, String fkName) {}

    private static class ForeignKeyBuilder {
        final String name;
        final List<String> columns = new ArrayList<>();
        String referencedSchema = "";
        String referencedTable = "";
        final List<String> referencedColumns = new ArrayList<>();
        String updateRule = "";
        String deleteRule = "";

        ForeignKeyBuilder(String name) {
            this.name = name;
        }

        JdbcForeignKeyInfo build() {
            return new JdbcForeignKeyInfo(
                    name,
                    List.copyOf(columns),
                    referencedSchema,
                    referencedTable,
                    List.copyOf(referencedColumns),
                    updateRule,
                    deleteRule);
        }
    }

    /**
     * Identity of a stored routine within a schema.
     *
     * <p>{@code type} participates in the identity because a procedure and a function may share a
     * name.
     *
     * @param schema the schema the routine belongs to
     * @param name the routine name
     * @param type the routine kind ({@code "PROCEDURE"} or {@code "FUNCTION"})
     */
    private record RoutineKey(String schema, String name, String type) {}

    private static class IndexBuilder {
        final String name;
        boolean unique;
        final List<JdbcIndexColumn> columns = new ArrayList<>();

        IndexBuilder(String name) {
            this.name = name;
        }

        JdbcIndexInfo build() {
            return new JdbcIndexInfo(name, unique, List.copyOf(columns));
        }
    }
}
