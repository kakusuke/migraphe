package io.github.kakusuke.migraphe.mysql.schema;

import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaDetail;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import java.util.List;

public record MySQLSchemaInfo(
        List<JdbcSchemaDetail> schemas,
        List<MySQLStorageEngineInfo> storageEngines,
        List<MySQLTableMetaInfo> tableMeta,
        List<MySQLTriggerInfo> triggers,
        List<MySQLRoutineInfo> routines,
        List<MySQLEventInfo> events,
        List<MySQLPartitionInfo> partitions)
        implements JdbcSchemaInfo {}
