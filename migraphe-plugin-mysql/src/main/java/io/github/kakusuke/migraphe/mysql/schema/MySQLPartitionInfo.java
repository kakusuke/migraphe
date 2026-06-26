package io.github.kakusuke.migraphe.mysql.schema;

import org.jspecify.annotations.Nullable;

/**
 * A summary of how a single MySQL table is partitioned, derived from {@code
 * information_schema.PARTITIONS}.
 *
 * <p>This data holder aggregates the per-partition rows of one partitioned table into a single
 * record (partition method, expression, and count) and is collected into {@link
 * MySQLSchemaInfo#partitions()}. Only tables that actually declare partitions contribute an entry.
 *
 * @param schema the schema the table belongs to (the {@code TABLE_SCHEMA} column)
 * @param tableName the partitioned table name (the {@code TABLE_NAME} column)
 * @param partitionMethod the partitioning method (the {@code PARTITION_METHOD} column, for example
 *     {@code "RANGE"}, {@code "LIST"}, {@code "HASH"}, or {@code "KEY"})
 * @param partitionExpression the column or expression the partitioning is keyed on (the {@code
 *     PARTITION_EXPRESSION} column), or {@code null} when not applicable
 * @param partitionCount the number of partitions defined for the table (a {@code COUNT(*)} over the
 *     table's partition rows)
 */
public record MySQLPartitionInfo(
        String schema,
        String tableName,
        String partitionMethod,
        @Nullable String partitionExpression,
        int partitionCount) {}
