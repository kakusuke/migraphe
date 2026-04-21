package io.github.kakusuke.migraphe.postgresql.schema;

import org.jspecify.annotations.Nullable;

public record PostgreSQLMaterializedViewInfo(
        String name,
        String schema,
        String definition,
        @Nullable String tablespace,
        @Nullable String owner) {

    public PostgreSQLMaterializedViewInfo(
            String name, String schema, String definition, @Nullable String tablespace) {
        this(name, schema, definition, tablespace, null);
    }
}
