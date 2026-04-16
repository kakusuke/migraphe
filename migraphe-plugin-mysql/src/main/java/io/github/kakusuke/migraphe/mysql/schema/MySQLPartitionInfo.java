package io.github.kakusuke.migraphe.mysql.schema;

import org.jspecify.annotations.Nullable;

public record MySQLPartitionInfo(
        String schema,
        String tableName,
        String partitionMethod,
        @Nullable String partitionExpression,
        int partitionCount) {}
