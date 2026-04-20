package io.github.kakusuke.migraphe.postgresql.schema;

import org.jspecify.annotations.Nullable;

public record PostgreSQLFunctionInfo(
        String schema,
        String name,
        String arguments,
        String returnType,
        String language,
        boolean isProcedure,
        @Nullable String owner) {

    public PostgreSQLFunctionInfo(
            String schema,
            String name,
            String arguments,
            String returnType,
            String language,
            boolean isProcedure) {
        this(schema, name, arguments, returnType, language, isProcedure, null);
    }
}
