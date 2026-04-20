package io.github.kakusuke.migraphe.mysql.schema;

import org.jspecify.annotations.Nullable;

public record MySQLRoutineInfo(
        String schema,
        String name,
        String type,
        String dataType,
        String parameterList,
        String securityType,
        @Nullable String definer) {

    public MySQLRoutineInfo(
            String schema,
            String name,
            String type,
            String dataType,
            String parameterList,
            String securityType) {
        this(schema, name, type, dataType, parameterList, securityType, null);
    }
}
