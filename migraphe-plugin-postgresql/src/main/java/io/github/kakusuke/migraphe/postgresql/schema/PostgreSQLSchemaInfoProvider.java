package io.github.kakusuke.migraphe.postgresql.schema;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.schema.SchemaInfoProvider;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfoProvider;
import io.github.kakusuke.migraphe.postgresql.PostgreSQLEnvironment;
import io.github.kakusuke.migraphe.postgresql.PostgreSQLException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts {@link PostgreSQLSchemaInfo} from a live {@link PostgreSQLEnvironment}.
 *
 * <p>This provider first delegates to {@link JdbcSchemaInfoProvider} for the generic JDBC schema
 * details (tables, views, columns), then opens a connection and queries the PostgreSQL system
 * catalogs ({@code pg_extension}, {@code pg_type}/{@code pg_enum}, {@code pg_sequences}, {@code
 * pg_proc}, {@code pg_trigger}, {@code pg_matviews}, {@code pg_partitioned_table}, {@code
 * pg_policy}) for the PostgreSQL-specific objects. Built-in schemas ({@code pg_catalog} and {@code
 * information_schema}) are excluded from the catalog queries. The combined result is returned as a
 * single {@link PostgreSQLSchemaInfo}.
 *
 * <p>It implements {@link SchemaInfoProvider} so generators can introspect a PostgreSQL database in
 * a dialect-aware way.
 */
public class PostgreSQLSchemaInfoProvider implements SchemaInfoProvider<PostgreSQLSchemaInfo> {

    /** Creates a new {@code PostgreSQLSchemaInfoProvider}. */
    public PostgreSQLSchemaInfoProvider() {}

    /**
     * Extracts the full PostgreSQL schema snapshot from the given environment.
     *
     * <p>The {@code environment} must be a {@link PostgreSQLEnvironment}; the generic JDBC schema
     * details are obtained via {@link JdbcSchemaInfoProvider} and merged with PostgreSQL-specific
     * catalog data queried over a fresh connection.
     *
     * @param environment the environment to introspect; must be a {@link PostgreSQLEnvironment}
     * @return the combined PostgreSQL schema information
     * @throws PostgreSQLException if {@code environment} is not a {@link PostgreSQLEnvironment}, or
     *     if a {@link SQLException} occurs while reading the catalogs
     */
    @Override
    public PostgreSQLSchemaInfo getSchemaInfo(Environment environment) {
        if (!(environment instanceof PostgreSQLEnvironment pgEnv)) {
            throw new PostgreSQLException(
                    "Environment must be a PostgreSQLEnvironment: "
                            + environment.getClass().getName());
        }
        var baseInfo = new JdbcSchemaInfoProvider().getSchemaInfo(environment);
        try (Connection conn = pgEnv.createConnection()) {
            return new PostgreSQLSchemaInfo(
                    baseInfo.schemas(),
                    List.copyOf(extractExtensions(conn)),
                    List.copyOf(extractEnums(conn)),
                    List.copyOf(extractSequences(conn)),
                    List.copyOf(extractFunctions(conn)),
                    List.copyOf(extractTriggers(conn)),
                    List.copyOf(extractMaterializedViews(conn)),
                    List.copyOf(extractPartitions(conn)),
                    List.copyOf(extractPolicies(conn)),
                    Map.copyOf(extractRelOwners(conn, "'r','p'")),
                    Map.copyOf(extractRelOwners(conn, "'v'")));
        } catch (SQLException e) {
            throw new PostgreSQLException("Failed to retrieve schema info", e);
        }
    }

    private Map<String, String> extractRelOwners(Connection conn, String relkindList)
            throws SQLException {
        Map<String, String> result = new HashMap<>();
        String sql =
                "SELECT n.nspname AS schema, c.relname AS name,"
                        + " pg_get_userbyid(c.relowner) AS owner"
                        + " FROM pg_class c"
                        + " JOIN pg_namespace n ON c.relnamespace = n.oid"
                        + " WHERE c.relkind IN ("
                        + relkindList
                        + ")"
                        + " AND n.nspname NOT IN ('pg_catalog', 'information_schema')";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.put(
                        rs.getString("schema") + "." + rs.getString("name"), rs.getString("owner"));
            }
        }
        return result;
    }

    private List<PostgreSQLExtensionInfo> extractExtensions(Connection conn) throws SQLException {
        List<PostgreSQLExtensionInfo> result = new ArrayList<>();
        String sql =
                "SELECT extname, extversion, pg_get_userbyid(extowner) AS owner FROM pg_extension";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(
                        new PostgreSQLExtensionInfo(
                                rs.getString("extname"),
                                rs.getString("extversion"),
                                rs.getString("owner")));
            }
        }
        return result;
    }

    private List<PostgreSQLEnumInfo> extractEnums(Connection conn) throws SQLException {
        List<PostgreSQLEnumInfo> result = new ArrayList<>();
        String sql =
                "SELECT t.typname AS name,"
                        + " array_agg(e.enumlabel ORDER BY e.enumsortorder) AS labels,"
                        + " pg_get_userbyid(t.typowner) AS owner"
                        + " FROM pg_type t"
                        + " JOIN pg_enum e ON t.oid = e.enumtypid"
                        + " JOIN pg_namespace n ON t.typnamespace = n.oid"
                        + " WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')"
                        + " GROUP BY t.typname, t.typowner";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String[] labels = (String[]) rs.getArray("labels").getArray();
                result.add(
                        new PostgreSQLEnumInfo(
                                rs.getString("name"), List.of(labels), rs.getString("owner")));
            }
        }
        return result;
    }

    private List<PostgreSQLSequenceInfo> extractSequences(Connection conn) throws SQLException {
        List<PostgreSQLSequenceInfo> result = new ArrayList<>();
        String sql =
                "SELECT s.schemaname, s.sequencename, s.data_type,"
                        + " s.start_value, s.min_value, s.max_value, s.increment_by, s.cycle,"
                        + " pg_get_userbyid(c.relowner) AS owner,"
                        + " ot.relname AS owner_table,"
                        + " oa.attname AS owner_column"
                        + " FROM pg_sequences s"
                        + " JOIN pg_class c ON c.relname = s.sequencename"
                        + " JOIN pg_namespace sn ON c.relnamespace = sn.oid"
                        + " AND sn.nspname = s.schemaname"
                        + " LEFT JOIN pg_depend d ON d.objid = c.oid"
                        + " AND d.classid = 'pg_class'::regclass"
                        + " AND d.refclassid = 'pg_class'::regclass"
                        + " AND d.deptype = 'a'"
                        + " LEFT JOIN pg_class ot ON ot.oid = d.refobjid"
                        + " LEFT JOIN pg_attribute oa ON oa.attrelid = d.refobjid"
                        + " AND oa.attnum = d.refobjsubid"
                        + " WHERE s.schemaname NOT IN ('pg_catalog', 'information_schema')";
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
                                rs.getString("owner_table"),
                                rs.getString("owner_column"),
                                rs.getString("owner")));
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
                        + " p.prokind = 'p' AS is_procedure,"
                        + " pg_get_userbyid(p.proowner) AS owner,"
                        + " p.prosrc AS definition"
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
                                rs.getBoolean("is_procedure"),
                                rs.getString("owner"),
                                rs.getString("definition")));
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
                "SELECT matviewname AS name, schemaname AS schema, definition, tablespace,"
                        + " matviewowner AS owner"
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
                                rs.getString("tablespace"),
                                rs.getString("owner")));
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
