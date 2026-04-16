package io.github.kakusuke.migraphe.mysql.schema;

public record MySQLTriggerInfo(
        String schema,
        String tableName,
        String name,
        String timing,
        String event,
        String statement) {}
