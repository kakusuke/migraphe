package io.github.kakusuke.migraphe.postgresql.schema;

import org.jspecify.annotations.Nullable;

public record PostgreSQLSequenceInfo(
        String schema,
        String name,
        String dataType,
        long startValue,
        long increment,
        long minValue,
        long maxValue,
        boolean cycle,
        @Nullable String ownerTable,
        @Nullable String ownerColumn) {}
