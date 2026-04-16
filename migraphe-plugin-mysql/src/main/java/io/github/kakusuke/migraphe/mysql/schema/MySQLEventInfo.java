package io.github.kakusuke.migraphe.mysql.schema;

import org.jspecify.annotations.Nullable;

public record MySQLEventInfo(
        String schema,
        String name,
        String type,
        @Nullable String intervalValue,
        @Nullable String intervalField,
        String status,
        String definition) {}
