package io.github.kakusuke.migraphe.mysql.schema;

import org.jspecify.annotations.Nullable;

/**
 * A single MySQL stored routine (procedure or function), as reported by {@code
 * information_schema.ROUTINES}.
 *
 * <p>This data holder mirrors one routine definition and is collected into {@link
 * MySQLSchemaInfo#routines()} so generators can document the stored procedures and functions in a
 * schema.
 *
 * @param schema the schema the routine belongs to (the {@code ROUTINE_SCHEMA} column)
 * @param name the routine name (the {@code ROUTINE_NAME} column)
 * @param type the routine kind (the {@code ROUTINE_TYPE} column, {@code "PROCEDURE"} or {@code
 *     "FUNCTION"})
 * @param dataType the return data type for functions (the {@code DTD_IDENTIFIER} column); empty
 *     string when the source value was {@code null} or the routine is a procedure
 * @param parameterList the formatted parameter list; currently always empty, as the source query
 *     does not populate it
 * @param securityType the SQL security context the routine runs under (the {@code SECURITY_TYPE}
 *     column, {@code "DEFINER"} or {@code "INVOKER"})
 * @param definer the account the routine executes as (the {@code DEFINER} column), or {@code null}
 *     when not available
 */
public record MySQLRoutineInfo(
        String schema,
        String name,
        String type,
        String dataType,
        String parameterList,
        String securityType,
        @Nullable String definer) {

    /**
     * Convenience constructor for routines without definer information, defaulting {@link
     * #definer()} to {@code null}.
     *
     * @param schema the schema the routine belongs to
     * @param name the routine name
     * @param type the routine kind ({@code "PROCEDURE"} or {@code "FUNCTION"})
     * @param dataType the return data type for functions
     * @param parameterList the formatted parameter list
     * @param securityType the SQL security context ({@code "DEFINER"} or {@code "INVOKER"})
     */
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
