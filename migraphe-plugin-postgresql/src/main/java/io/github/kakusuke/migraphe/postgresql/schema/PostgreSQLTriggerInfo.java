package io.github.kakusuke.migraphe.postgresql.schema;

import java.util.List;

public record PostgreSQLTriggerInfo(
        String name,
        String schema,
        String tableName,
        String timing,
        List<String> events,
        String functionName,
        boolean isConstraint) {}
