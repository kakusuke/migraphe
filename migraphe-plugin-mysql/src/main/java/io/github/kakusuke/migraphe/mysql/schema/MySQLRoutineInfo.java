package io.github.kakusuke.migraphe.mysql.schema;

import java.util.List;
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
 * @param parameters the declared parameters in ordinal order, read from {@code
 *     information_schema.PARAMETERS}; empty when the routine declares none. A function's return
 *     value, which that table reports at ordinal position {@code 0}, is not included.
 * @param securityType the SQL security context the routine runs under (the {@code SECURITY_TYPE}
 *     column, {@code "DEFINER"} or {@code "INVOKER"})
 * @param definer the account the routine executes as (the {@code DEFINER} column), or {@code null}
 *     when not available
 * @param definition the routine body (the {@code ROUTINE_DEFINITION} column), or {@code null} when
 *     the connected account lacks the privilege to read it
 */
public record MySQLRoutineInfo(
        String schema,
        String name,
        String type,
        String dataType,
        List<MySQLParameterInfo> parameters,
        String securityType,
        @Nullable String definer,
        @Nullable String definition) {

    /**
     * Convenience constructor for routines without definer or body information, defaulting {@link
     * #definer()} and {@link #definition()} to {@code null}.
     *
     * @param schema the schema the routine belongs to
     * @param name the routine name
     * @param type the routine kind ({@code "PROCEDURE"} or {@code "FUNCTION"})
     * @param dataType the return data type for functions
     * @param parameters the declared parameters in ordinal order
     * @param securityType the SQL security context ({@code "DEFINER"} or {@code "INVOKER"})
     */
    public MySQLRoutineInfo(
            String schema,
            String name,
            String type,
            String dataType,
            List<MySQLParameterInfo> parameters,
            String securityType) {
        this(schema, name, type, dataType, parameters, securityType, null, null);
    }

    /**
     * Convenience constructor for routines whose body was not captured, defaulting {@link
     * #definition()} to {@code null}.
     *
     * @param schema the schema the routine belongs to
     * @param name the routine name
     * @param type the routine kind ({@code "PROCEDURE"} or {@code "FUNCTION"})
     * @param dataType the return data type for functions
     * @param parameters the declared parameters in ordinal order
     * @param securityType the SQL security context ({@code "DEFINER"} or {@code "INVOKER"})
     * @param definer the account the routine executes as, or {@code null} when not available
     */
    public MySQLRoutineInfo(
            String schema,
            String name,
            String type,
            String dataType,
            List<MySQLParameterInfo> parameters,
            String securityType,
            @Nullable String definer) {
        this(schema, name, type, dataType, parameters, securityType, definer, null);
    }
}
