package io.github.kakusuke.migraphe.mysql.schema;

import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaDetail;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import java.util.List;
import java.util.Map;

/**
 * MySQL-specific snapshot of a database's schema, extending the generic JDBC schema model with
 * objects that have no portable {@link java.sql.DatabaseMetaData} representation.
 *
 * <p>This is the typed data object produced by the {@code mysql-schema} generator source (see
 * {@link MySQLSchemaSourcePlugin}) and consumed by output plugins such as the MySQL Markdown
 * generator. It implements {@link JdbcSchemaInfo} so that generic JDBC output plugins can still
 * read the portable {@link #schemas()} detail, while MySQL-aware outputs can additionally render
 * the MySQL-only collections (storage engines, table metadata, triggers, routines, events,
 * partitions) gathered from {@code information_schema}.
 *
 * @param schemas the portable per-schema detail records, one entry per discovered schema, as
 *     extracted through {@link java.sql.DatabaseMetaData}; never {@code null}
 * @param storageEngines the storage engines reported by {@code information_schema.ENGINES}
 * @param tableMeta the MySQL-specific metadata for each base table (engine, collation, row format,
 *     comment)
 * @param triggers the triggers defined in the inspected schema
 * @param routines the stored procedures and functions defined in the inspected schema
 * @param events the scheduled events defined in the inspected schema
 * @param partitions the table-partitioning summaries for partitioned tables in the schema
 * @param viewDefiners a map from {@code schema.viewName} to the {@code DEFINER} account of each
 *     view, as reported by {@code information_schema.VIEWS}
 */
public record MySQLSchemaInfo(
        List<JdbcSchemaDetail> schemas,
        List<MySQLStorageEngineInfo> storageEngines,
        List<MySQLTableMetaInfo> tableMeta,
        List<MySQLTriggerInfo> triggers,
        List<MySQLRoutineInfo> routines,
        List<MySQLEventInfo> events,
        List<MySQLPartitionInfo> partitions,
        Map<String, String> viewDefiners)
        implements JdbcSchemaInfo {

    /**
     * Convenience constructor for callers that have no view-definer information, defaulting {@link
     * #viewDefiners()} to an empty map.
     *
     * @param schemas the portable per-schema detail records; never {@code null}
     * @param storageEngines the storage engines reported by {@code information_schema.ENGINES}
     * @param tableMeta the MySQL-specific metadata for each base table
     * @param triggers the triggers defined in the inspected schema
     * @param routines the stored procedures and functions defined in the inspected schema
     * @param events the scheduled events defined in the inspected schema
     * @param partitions the table-partitioning summaries for partitioned tables in the schema
     */
    public MySQLSchemaInfo(
            List<JdbcSchemaDetail> schemas,
            List<MySQLStorageEngineInfo> storageEngines,
            List<MySQLTableMetaInfo> tableMeta,
            List<MySQLTriggerInfo> triggers,
            List<MySQLRoutineInfo> routines,
            List<MySQLEventInfo> events,
            List<MySQLPartitionInfo> partitions) {
        this(schemas, storageEngines, tableMeta, triggers, routines, events, partitions, Map.of());
    }
}
