package io.github.kakusuke.migraphe.mysql.schema;

import org.jspecify.annotations.Nullable;

/**
 * A single MySQL scheduled event, as reported by {@code information_schema.EVENTS}.
 *
 * <p>This data holder mirrors one event definition and is collected into {@link
 * MySQLSchemaInfo#events()} so generators can document the schema's scheduled jobs. For recurring
 * events the recurrence is described by the {@code intervalValue}/{@code intervalField} pair; for
 * one-time events these are {@code null}.
 *
 * @param schema the schema the event belongs to (the {@code EVENT_SCHEMA} column)
 * @param name the event name (the {@code EVENT_NAME} column)
 * @param type the event kind (the {@code EVENT_TYPE} column, {@code "ONE TIME"} or {@code
 *     "RECURRING"})
 * @param intervalValue the numeric part of a recurring event's interval (the {@code INTERVAL_VALUE}
 *     column), or {@code null} for one-time events
 * @param intervalField the unit of a recurring event's interval (the {@code INTERVAL_FIELD} column,
 *     for example {@code "DAY"} or {@code "HOUR"}), or {@code null} for one-time events
 * @param status the event's scheduling status (the {@code STATUS} column, for example {@code
 *     "ENABLED"}, {@code "DISABLED"}, or {@code "SLAVESIDE_DISABLED"})
 * @param definition the SQL statement the event executes (the {@code EVENT_DEFINITION} column)
 * @param definer the account the event executes as (the {@code DEFINER} column), or {@code null}
 *     when not available
 */
public record MySQLEventInfo(
        String schema,
        String name,
        String type,
        @Nullable String intervalValue,
        @Nullable String intervalField,
        String status,
        String definition,
        @Nullable String definer) {

    /**
     * Convenience constructor for events without definer information, defaulting {@link #definer()}
     * to {@code null}.
     *
     * @param schema the schema the event belongs to
     * @param name the event name
     * @param type the event kind ({@code "ONE TIME"} or {@code "RECURRING"})
     * @param intervalValue the numeric part of a recurring event's interval, or {@code null} for
     *     one-time events
     * @param intervalField the unit of a recurring event's interval, or {@code null} for one-time
     *     events
     * @param status the event's scheduling status
     * @param definition the SQL statement the event executes
     */
    public MySQLEventInfo(
            String schema,
            String name,
            String type,
            @Nullable String intervalValue,
            @Nullable String intervalField,
            String status,
            String definition) {
        this(schema, name, type, intervalValue, intervalField, status, definition, null);
    }
}
