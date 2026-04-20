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
        @Nullable String ownerColumn,
        @Nullable String owner) {

    public PostgreSQLSequenceInfo(
            String schema,
            String name,
            String dataType,
            long startValue,
            long increment,
            long minValue,
            long maxValue,
            boolean cycle,
            @Nullable String ownerTable,
            @Nullable String ownerColumn) {
        this(
                schema,
                name,
                dataType,
                startValue,
                increment,
                minValue,
                maxValue,
                cycle,
                ownerTable,
                ownerColumn,
                null);
    }
}
