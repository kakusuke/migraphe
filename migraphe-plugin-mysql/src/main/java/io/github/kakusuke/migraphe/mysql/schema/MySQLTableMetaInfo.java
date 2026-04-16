package io.github.kakusuke.migraphe.mysql.schema;

import org.jspecify.annotations.Nullable;

public record MySQLTableMetaInfo(
        String schema,
        String tableName,
        String engine,
        String collation,
        String rowFormat,
        @Nullable String tableComment) {}
