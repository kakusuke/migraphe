package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/** テーブル情報。 */
public record JdbcTableInfo(
        String name,
        String remarks,
        List<JdbcColumnInfo> columns,
        JdbcPrimaryKeyInfo primaryKey,
        List<JdbcForeignKeyInfo> foreignKeys,
        List<JdbcForeignKeyInfo> exportedKeys,
        List<JdbcCheckConstraintInfo> checkConstraints,
        List<JdbcIndexInfo> indexes,
        List<JdbcPrivilegeInfo> privileges) {}
