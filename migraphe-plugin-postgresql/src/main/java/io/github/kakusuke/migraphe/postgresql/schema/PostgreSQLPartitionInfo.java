package io.github.kakusuke.migraphe.postgresql.schema;

public record PostgreSQLPartitionInfo(
        String name, String schema, String strategy, String partitionKey) {}
