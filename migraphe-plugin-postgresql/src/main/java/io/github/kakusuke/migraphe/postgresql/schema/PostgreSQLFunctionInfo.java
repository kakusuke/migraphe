package io.github.kakusuke.migraphe.postgresql.schema;

public record PostgreSQLFunctionInfo(
        String schema,
        String name,
        String arguments,
        String returnType,
        String language,
        boolean isProcedure) {}
