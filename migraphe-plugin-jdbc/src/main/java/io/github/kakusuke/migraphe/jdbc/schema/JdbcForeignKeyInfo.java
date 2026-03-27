package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/** 外部キー情報。 */
public record JdbcForeignKeyInfo(
        String name,
        List<String> columns,
        String referencedSchema,
        String referencedTable,
        List<String> referencedColumns,
        String updateRule,
        String deleteRule) {}
