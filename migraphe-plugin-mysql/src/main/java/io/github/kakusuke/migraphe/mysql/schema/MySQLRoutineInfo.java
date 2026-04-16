package io.github.kakusuke.migraphe.mysql.schema;

public record MySQLRoutineInfo(
        String schema,
        String name,
        String type,
        String dataType,
        String parameterList,
        String securityType) {}
