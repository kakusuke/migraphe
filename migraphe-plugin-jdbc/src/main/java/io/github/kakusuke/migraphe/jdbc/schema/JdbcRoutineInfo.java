package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/**
 * Stored procedure or function (routine) information.
 *
 * <p>Immutable data holder describing a database routine and its parameters. The portable JDBC
 * metadata API exposes routines only partially; this record is primarily populated by
 * dialect-specific providers.
 *
 * @param name the routine name
 * @param type whether the routine is a {@link RoutineType#PROCEDURE} or {@link
 *     RoutineType#FUNCTION}
 * @param remarks the routine's description or comment, or an empty string if none
 * @param body the routine source/body, or an empty string if unavailable
 * @param parameters the routine's parameters (and return/result columns) in declaration order
 * @see RoutineType
 * @see JdbcRoutineColumnInfo
 */
public record JdbcRoutineInfo(
        String name,
        RoutineType type,
        String remarks,
        String body,
        List<JdbcRoutineColumnInfo> parameters) {}
