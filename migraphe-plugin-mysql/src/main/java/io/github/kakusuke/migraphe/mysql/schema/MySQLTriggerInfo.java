package io.github.kakusuke.migraphe.mysql.schema;

import org.jspecify.annotations.Nullable;

public record MySQLTriggerInfo(
        String schema,
        String tableName,
        String name,
        String timing,
        String event,
        String statement,
        @Nullable String definer) {

    public MySQLTriggerInfo(
            String schema,
            String tableName,
            String name,
            String timing,
            String event,
            String statement) {
        this(schema, tableName, name, timing, event, statement, null);
    }
}
