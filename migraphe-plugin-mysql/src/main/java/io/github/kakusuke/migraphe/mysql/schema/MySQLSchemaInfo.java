package io.github.kakusuke.migraphe.mysql.schema;

import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaDetail;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import java.util.List;
import java.util.Map;

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
