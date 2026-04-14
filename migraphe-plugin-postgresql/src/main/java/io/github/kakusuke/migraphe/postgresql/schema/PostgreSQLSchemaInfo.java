package io.github.kakusuke.migraphe.postgresql.schema;

import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaDetail;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import java.util.List;

public record PostgreSQLSchemaInfo(
        List<JdbcSchemaDetail> schemas,
        List<PostgreSQLExtensionInfo> extensions,
        List<PostgreSQLEnumInfo> enums,
        List<PostgreSQLSequenceInfo> sequences,
        List<PostgreSQLFunctionInfo> functions,
        List<PostgreSQLTriggerInfo> triggers,
        List<PostgreSQLMaterializedViewInfo> materializedViews,
        List<PostgreSQLPartitionInfo> partitions,
        List<PostgreSQLPolicyInfo> policies)
        implements JdbcSchemaInfo {}
