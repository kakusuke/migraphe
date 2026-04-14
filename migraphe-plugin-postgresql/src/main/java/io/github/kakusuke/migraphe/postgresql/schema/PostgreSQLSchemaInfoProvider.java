package io.github.kakusuke.migraphe.postgresql.schema;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.schema.SchemaInfoProvider;
import io.github.kakusuke.migraphe.postgresql.PostgreSQLEnvironment;
import io.github.kakusuke.migraphe.postgresql.PostgreSQLException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class PostgreSQLSchemaInfoProvider implements SchemaInfoProvider<PostgreSQLSchemaInfo> {

    @Override
    public PostgreSQLSchemaInfo getSchemaInfo(Environment environment) {
        if (!(environment instanceof PostgreSQLEnvironment pgEnv)) {
            throw new PostgreSQLException(
                    "Environment must be a PostgreSQLEnvironment: "
                            + environment.getClass().getName());
        }
        try (Connection conn = pgEnv.createConnection()) {
            return new PostgreSQLSchemaInfo(
                    List.of(),
                    List.copyOf(extractExtensions(conn)),
                    List.copyOf(extractEnums(conn)),
                    List.copyOf(extractSequences(conn)),
                    List.copyOf(extractFunctions(conn)),
                    List.copyOf(extractTriggers(conn)),
                    List.copyOf(extractMaterializedViews(conn)),
                    List.copyOf(extractPartitions(conn)),
                    List.copyOf(extractPolicies(conn)));
        } catch (SQLException e) {
            throw new PostgreSQLException("Failed to retrieve schema info", e);
        }
    }

    private List<PostgreSQLExtensionInfo> extractExtensions(Connection conn) throws SQLException {
        List<PostgreSQLExtensionInfo> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT extname, extversion FROM pg_extension")) {
            while (rs.next()) {
                result.add(
                        new PostgreSQLExtensionInfo(
                                rs.getString("extname"), rs.getString("extversion")));
            }
        }
        return result;
    }

    private List<PostgreSQLEnumInfo> extractEnums(Connection conn) throws SQLException {
        List<PostgreSQLEnumInfo> result = new ArrayList<>();
        String sql =
                "SELECT t.typname AS name,"
                        + " array_agg(e.enumlabel ORDER BY e.enumsortorder) AS labels"
                        + " FROM pg_type t"
                        + " JOIN pg_enum e ON t.oid = e.enumtypid"
                        + " JOIN pg_namespace n ON t.typnamespace = n.oid"
                        + " WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')"
                        + " GROUP BY t.typname";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String[] labels = (String[]) rs.getArray("labels").getArray();
                result.add(new PostgreSQLEnumInfo(rs.getString("name"), List.of(labels)));
            }
        }
        return result;
    }

    private List<PostgreSQLSequenceInfo> extractSequences(Connection conn) throws SQLException {
        List<PostgreSQLSequenceInfo> result = new ArrayList<>();
        String sql =
                "SELECT schemaname, sequencename, data_type,"
                        + " start_value, min_value, max_value, increment_by, cycle"
                        + " FROM pg_sequences"
                        + " WHERE schemaname NOT IN ('pg_catalog', 'information_schema')";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(
                        new PostgreSQLSequenceInfo(
                                rs.getString("schemaname"),
                                rs.getString("sequencename"),
                                rs.getString("data_type"),
                                rs.getLong("start_value"),
                                rs.getLong("increment_by"),
                                rs.getLong("min_value"),
                                rs.getLong("max_value"),
                                rs.getBoolean("cycle"),
                                null,
                                null));
            }
        }
        return result;
    }

    private List<PostgreSQLFunctionInfo> extractFunctions(Connection conn) throws SQLException {
        List<PostgreSQLFunctionInfo> result = new ArrayList<>();
        String sql =
                "SELECT n.nspname AS schema, p.proname AS name,"
                        + " pg_get_function_arguments(p.oid) AS arguments,"
                        + " pg_get_function_result(p.oid) AS return_type,"
                        + " l.lanname AS language,"
                        + " p.prokind = 'p' AS is_procedure"
                        + " FROM pg_proc p"
                        + " JOIN pg_namespace n ON p.pronamespace = n.oid"
                        + " JOIN pg_language l ON p.prolang = l.oid"
                        + " WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(
                        new PostgreSQLFunctionInfo(
                                rs.getString("schema"),
                                rs.getString("name"),
                                rs.getString("arguments"),
                                rs.getString("return_type"),
                                rs.getString("language"),
                                rs.getBoolean("is_procedure")));
            }
        }
        return result;
    }

    private List<PostgreSQLTriggerInfo> extractTriggers(Connection conn) throws SQLException {
        List<PostgreSQLTriggerInfo> result = new ArrayList<>();
        String sql =
                "SELECT t.tgname AS name, n.nspname AS schema, c.relname AS table_name,"
                        + " CASE WHEN (t.tgtype::int & 2) = 2 THEN 'BEFORE'"
                        + " WHEN (t.tgtype::int & 64) = 64 THEN 'INSTEAD OF'"
                        + " ELSE 'AFTER' END AS timing,"
                        + " t.tgtype::int AS tg_type,"
                        + " p.proname AS function_name,"
                        + " t.tgconstraint <> 0 AS is_constraint"
                        + " FROM pg_trigger t"
                        + " JOIN pg_class c ON t.tgrelid = c.oid"
                        + " JOIN pg_namespace n ON c.relnamespace = n.oid"
                        + " JOIN pg_proc p ON t.tgfoid = p.oid"
                        + " WHERE NOT t.tgisinternal"
                        + " AND n.nspname NOT IN ('pg_catalog', 'information_schema')";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int tgType = rs.getInt("tg_type");
                List<String> events = new ArrayList<>();
                if ((tgType & 4) != 0) events.add("INSERT");
                if ((tgType & 8) != 0) events.add("DELETE");
                if ((tgType & 16) != 0) events.add("UPDATE");
                if ((tgType & 32) != 0) events.add("TRUNCATE");
                result.add(
                        new PostgreSQLTriggerInfo(
                                rs.getString("name"),
                                rs.getString("schema"),
                                rs.getString("table_name"),
                                rs.getString("timing"),
                                List.copyOf(events),
                                rs.getString("function_name"),
                                rs.getBoolean("is_constraint")));
            }
        }
        return result;
    }

    private List<PostgreSQLMaterializedViewInfo> extractMaterializedViews(Connection conn)
            throws SQLException {
        List<PostgreSQLMaterializedViewInfo> result = new ArrayList<>();
        String sql =
                "SELECT matviewname AS name, schemaname AS schema, definition, tablespace"
                        + " FROM pg_matviews"
                        + " WHERE schemaname NOT IN ('pg_catalog', 'information_schema')";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(
                        new PostgreSQLMaterializedViewInfo(
                                rs.getString("name"),
                                rs.getString("schema"),
                                rs.getString("definition"),
                                rs.getString("tablespace")));
            }
        }
        return result;
    }

    private List<PostgreSQLPartitionInfo> extractPartitions(Connection conn) throws SQLException {
        List<PostgreSQLPartitionInfo> result = new ArrayList<>();
        String sql =
                "SELECT c.relname AS name, n.nspname AS schema,"
                        + " CASE pt.partstrat"
                        + " WHEN 'r' THEN 'RANGE'"
                        + " WHEN 'l' THEN 'LIST'"
                        + " WHEN 'h' THEN 'HASH' END AS strategy,"
                        + " pg_get_partkeydef(c.oid) AS partition_key"
                        + " FROM pg_partitioned_table pt"
                        + " JOIN pg_class c ON pt.partrelid = c.oid"
                        + " JOIN pg_namespace n ON c.relnamespace = n.oid"
                        + " WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(
                        new PostgreSQLPartitionInfo(
                                rs.getString("name"),
                                rs.getString("schema"),
                                rs.getString("strategy"),
                                rs.getString("partition_key")));
            }
        }
        return result;
    }

    private List<PostgreSQLPolicyInfo> extractPolicies(Connection conn) throws SQLException {
        List<PostgreSQLPolicyInfo> result = new ArrayList<>();
        String sql =
                "SELECT pol.polname AS name, n.nspname AS schema,"
                        + " c.relname AS table_name,"
                        + " CASE pol.polcmd"
                        + " WHEN 'r' THEN 'SELECT'"
                        + " WHEN 'a' THEN 'INSERT'"
                        + " WHEN 'w' THEN 'UPDATE'"
                        + " WHEN 'd' THEN 'DELETE'"
                        + " WHEN '*' THEN 'ALL' END AS command,"
                        + " pg_get_expr(pol.polqual, pol.polrelid) AS using_expr,"
                        + " pg_get_expr(pol.polwithcheck, pol.polrelid) AS with_check_expr"
                        + " FROM pg_policy pol"
                        + " JOIN pg_class c ON pol.polrelid = c.oid"
                        + " JOIN pg_namespace n ON c.relnamespace = n.oid"
                        + " WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(
                        new PostgreSQLPolicyInfo(
                                rs.getString("name"),
                                rs.getString("schema"),
                                rs.getString("table_name"),
                                rs.getString("command"),
                                List.of(),
                                rs.getString("using_expr"),
                                rs.getString("with_check_expr")));
            }
        }
        return result;
    }
}
