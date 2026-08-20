package io.github.kakusuke.migraphe.postgresql.schema;

import org.jspecify.annotations.Nullable;

/**
 * Schema information for a single PostgreSQL function or stored procedure.
 *
 * <p>The values are read from the {@code pg_proc} catalog, with the argument and return-type
 * strings formatted by PostgreSQL's {@code pg_get_function_arguments} / {@code
 * pg_get_function_result} helpers. PostgreSQL stores both functions and procedures in {@code
 * pg_proc}, distinguished by {@code prokind}; {@link #isProcedure()} reflects that distinction.
 * This record is one of the PostgreSQL-specific elements collected by {@link
 * PostgreSQLSchemaInfoProvider} and exposed through {@link PostgreSQLSchemaInfo}.
 *
 * @param schema the schema that contains the routine
 * @param name the routine name
 * @param arguments the formatted argument list (for example {@code "a integer, b text"}), as
 *     produced by {@code pg_get_function_arguments}
 * @param returnType the formatted return type, as produced by {@code pg_get_function_result}
 * @param language the implementation language of the routine (for example {@code "sql"} or {@code
 *     "plpgsql"})
 * @param isProcedure {@code true} if the routine is a stored procedure ({@code prokind = 'p'}),
 *     {@code false} if it is a function
 * @param owner the role name that owns the routine, or {@code null} if not captured
 * @param definition the routine body as stored in {@code pg_proc.prosrc}, or {@code null} if not
 *     captured. This is the body only, mirroring MySQL's {@code ROUTINE_DEFINITION}; for routines
 *     implemented in C it is the symbol name rather than SQL.
 */
public record PostgreSQLFunctionInfo(
        String schema,
        String name,
        String arguments,
        String returnType,
        String language,
        boolean isProcedure,
        @Nullable String owner,
        @Nullable String definition) {

    /**
     * Creates routine information without a known owner or body.
     *
     * @param schema the schema that contains the routine
     * @param name the routine name
     * @param arguments the formatted argument list
     * @param returnType the formatted return type
     * @param language the implementation language of the routine
     * @param isProcedure {@code true} if the routine is a stored procedure, {@code false} if a
     *     function
     */
    public PostgreSQLFunctionInfo(
            String schema,
            String name,
            String arguments,
            String returnType,
            String language,
            boolean isProcedure) {
        this(schema, name, arguments, returnType, language, isProcedure, null, null);
    }

    /**
     * Creates routine information whose body was not captured.
     *
     * @param schema the schema that contains the routine
     * @param name the routine name
     * @param arguments the formatted argument list
     * @param returnType the formatted return type
     * @param language the implementation language of the routine
     * @param isProcedure {@code true} if the routine is a stored procedure, {@code false} if a
     *     function
     * @param owner the role name that owns the routine, or {@code null} if not captured
     */
    public PostgreSQLFunctionInfo(
            String schema,
            String name,
            String arguments,
            String returnType,
            String language,
            boolean isProcedure,
            @Nullable String owner) {
        this(schema, name, arguments, returnType, language, isProcedure, owner, null);
    }
}
