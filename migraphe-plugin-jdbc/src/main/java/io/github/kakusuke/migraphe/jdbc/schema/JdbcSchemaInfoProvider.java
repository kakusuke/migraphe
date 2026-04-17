package io.github.kakusuke.migraphe.jdbc.schema;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.schema.SchemaInfoProvider;
import io.github.kakusuke.migraphe.jdbc.JdbcEnvironment;
import io.github.kakusuke.migraphe.jdbc.JdbcException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** JDBC DatabaseMetaData を使用してスキーマ情報を取得するプロバイダ。 */
public class JdbcSchemaInfoProvider implements SchemaInfoProvider<JdbcSchemaInfo> {

    @Override
    public JdbcSchemaInfo getSchemaInfo(Environment environment) {
        if (!(environment instanceof JdbcEnvironment jdbcEnv)) {
            throw new JdbcException(
                    "Environment must be a JdbcEnvironment: " + environment.getClass().getName());
        }
        try (Connection conn = jdbcEnv.createConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            List<String> schemaNames = discoverSchemas(meta);
            List<JdbcSchemaDetail> schemas = new ArrayList<>();
            for (String schemaName : schemaNames) {
                schemas.add(buildSchemaDetail(meta, schemaName));
            }
            return new DefaultJdbcSchemaInfo(schemas);
        } catch (SQLException e) {
            throw new JdbcException("Failed to retrieve schema info", e);
        }
    }

    private List<String> discoverSchemas(DatabaseMetaData meta) throws SQLException {
        List<String> schemas = new ArrayList<>();
        try (ResultSet rs = meta.getSchemas()) {
            while (rs.next()) {
                String name = rs.getString("TABLE_SCHEM");
                if (name != null && !name.equalsIgnoreCase("INFORMATION_SCHEMA")) {
                    schemas.add(name);
                }
            }
        }
        return schemas;
    }

    private JdbcSchemaDetail buildSchemaDetail(DatabaseMetaData meta, String schemaName)
            throws SQLException {
        List<JdbcTableInfo> tables = buildTables(meta, schemaName);
        List<JdbcViewInfo> views = buildViews(meta, schemaName);
        return new JdbcSchemaDetail(
                schemaName, tables, views, List.of(), List.of(), List.of(), List.of());
    }

    private List<JdbcTableInfo> buildTables(DatabaseMetaData meta, String schemaName)
            throws SQLException {
        List<JdbcTableInfo> tables = new ArrayList<>();
        try (ResultSet rs = meta.getTables(null, schemaName, null, new String[] {"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String remarks = nullToEmpty(rs.getString("REMARKS"));
                List<JdbcColumnInfo> columns = buildColumns(meta, schemaName, tableName);
                JdbcPrimaryKeyInfo primaryKey = buildPrimaryKey(meta, schemaName, tableName);
                List<JdbcForeignKeyInfo> foreignKeys =
                        buildForeignKeys(meta, schemaName, tableName);
                List<JdbcForeignKeyInfo> exportedKeys =
                        buildExportedKeys(meta, schemaName, tableName);
                List<JdbcIndexInfo> indexes = buildIndexes(meta, schemaName, tableName);
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

    private List<JdbcViewInfo> buildViews(DatabaseMetaData meta, String schemaName)
            throws SQLException {
        List<JdbcViewInfo> views = new ArrayList<>();
        try (ResultSet rs = meta.getTables(null, schemaName, null, new String[] {"VIEW"})) {
            while (rs.next()) {
                String viewName = rs.getString("TABLE_NAME");
                String remarks = nullToEmpty(rs.getString("REMARKS"));
                List<JdbcColumnInfo> columns = buildColumns(meta, schemaName, viewName);
                views.add(new JdbcViewInfo(viewName, remarks, columns, ""));
            }
        }
        return views;
    }

    private List<JdbcColumnInfo> buildColumns(
            DatabaseMetaData meta, String schemaName, String tableName) throws SQLException {
        List<JdbcColumnInfo> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(null, schemaName, tableName, null)) {
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
            DatabaseMetaData meta, String schemaName, String tableName) throws SQLException {
        String pkName = "";
        List<String> columns = new ArrayList<>();
        try (ResultSet rs = meta.getPrimaryKeys(null, schemaName, tableName)) {
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
            DatabaseMetaData meta, String schemaName, String tableName) throws SQLException {
        return buildKeyInfo(meta.getImportedKeys(null, schemaName, tableName), true);
    }

    private List<JdbcForeignKeyInfo> buildExportedKeys(
            DatabaseMetaData meta, String schemaName, String tableName) throws SQLException {
        return buildKeyInfo(meta.getExportedKeys(null, schemaName, tableName), false);
    }

    private List<JdbcForeignKeyInfo> buildKeyInfo(ResultSet rs, boolean imported)
            throws SQLException {
        Map<String, ForeignKeyBuilder> builders = new LinkedHashMap<>();
        try (rs) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                if (fkName == null) {
                    fkName = "";
                }
                ForeignKeyBuilder builder =
                        builders.computeIfAbsent(fkName, ForeignKeyBuilder::new);
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
            DatabaseMetaData meta, String schemaName, String tableName) throws SQLException {
        Map<String, IndexBuilder> builders = new LinkedHashMap<>();
        try (ResultSet rs = meta.getIndexInfo(null, schemaName, tableName, false, false)) {
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
