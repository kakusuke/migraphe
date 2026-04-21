package io.github.kakusuke.migraphe.postgresql.schema;

import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaDetail;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import java.util.List;
import java.util.Map;

public record PostgreSQLSchemaInfo(
        List<JdbcSchemaDetail> schemas,
        List<PostgreSQLExtensionInfo> extensions,
        List<PostgreSQLEnumInfo> enums,
        List<PostgreSQLSequenceInfo> sequences,
        List<PostgreSQLFunctionInfo> functions,
        List<PostgreSQLTriggerInfo> triggers,
        List<PostgreSQLMaterializedViewInfo> materializedViews,
        List<PostgreSQLPartitionInfo> partitions,
        List<PostgreSQLPolicyInfo> policies,
        Map<String, String> tableOwners,
        Map<String, String> viewOwners)
        implements JdbcSchemaInfo {

    public PostgreSQLSchemaInfo(
            List<JdbcSchemaDetail> schemas,
            List<PostgreSQLExtensionInfo> extensions,
            List<PostgreSQLEnumInfo> enums,
            List<PostgreSQLSequenceInfo> sequences,
            List<PostgreSQLFunctionInfo> functions,
            List<PostgreSQLTriggerInfo> triggers,
            List<PostgreSQLMaterializedViewInfo> materializedViews,
            List<PostgreSQLPartitionInfo> partitions,
            List<PostgreSQLPolicyInfo> policies) {
        this(
                schemas,
                extensions,
                enums,
                sequences,
                functions,
                triggers,
                materializedViews,
                partitions,
                policies,
                Map.of(),
                Map.of());
    }
}
