package io.github.kakusuke.migraphe.postgresql.schema;

/**
 * Schema information for a single PostgreSQL partitioned table.
 *
 * <p>Declarative partitioning splits one logical table into multiple physical partitions according
 * to a partition key and strategy. The values are read from the {@code pg_partitioned_table}
 * catalog, with the key formatted by {@code pg_get_partkeydef}. This record is one of the
 * PostgreSQL-specific elements collected by {@link PostgreSQLSchemaInfoProvider} and exposed
 * through {@link PostgreSQLSchemaInfo}.
 *
 * @param name the name of the partitioned (parent) table
 * @param schema the schema that contains the partitioned table
 * @param strategy the partitioning strategy: {@code "RANGE"}, {@code "LIST"}, or {@code "HASH"}
 * @param partitionKey the partition key expression, as produced by {@code pg_get_partkeydef}
 */
public record PostgreSQLPartitionInfo(
        String name, String schema, String strategy, String partitionKey) {}
