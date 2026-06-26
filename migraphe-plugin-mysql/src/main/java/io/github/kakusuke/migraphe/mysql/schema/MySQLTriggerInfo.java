package io.github.kakusuke.migraphe.mysql.schema;

import org.jspecify.annotations.Nullable;

/**
 * A single MySQL trigger, as reported by {@code information_schema.TRIGGERS}.
 *
 * <p>This data holder mirrors one trigger definition and is collected into {@link
 * MySQLSchemaInfo#triggers()} so generators can document the triggers attached to a schema's
 * tables.
 *
 * @param schema the schema the trigger belongs to (the {@code TRIGGER_SCHEMA} column)
 * @param tableName the table the trigger is attached to (the {@code EVENT_OBJECT_TABLE} column)
 * @param name the trigger name (the {@code TRIGGER_NAME} column)
 * @param timing when the trigger fires relative to the row event (the {@code ACTION_TIMING} column,
 *     {@code "BEFORE"} or {@code "AFTER"})
 * @param event the row event the trigger responds to (the {@code EVENT_MANIPULATION} column, {@code
 *     "INSERT"}, {@code "UPDATE"}, or {@code "DELETE"})
 * @param statement the trigger body executed when it fires (the {@code ACTION_STATEMENT} column)
 * @param definer the account the trigger executes as (the {@code DEFINER} column), or {@code null}
 *     when not available
 */
public record MySQLTriggerInfo(
        String schema,
        String tableName,
        String name,
        String timing,
        String event,
        String statement,
        @Nullable String definer) {

    /**
     * Convenience constructor for triggers without definer information, defaulting {@link
     * #definer()} to {@code null}.
     *
     * @param schema the schema the trigger belongs to
     * @param tableName the table the trigger is attached to
     * @param name the trigger name
     * @param timing when the trigger fires ({@code "BEFORE"} or {@code "AFTER"})
     * @param event the row event the trigger responds to ({@code "INSERT"}, {@code "UPDATE"}, or
     *     {@code "DELETE"})
     * @param statement the trigger body executed when it fires
     */
    public MySQLTriggerInfo(
            String schema,
            String tableName,
            String name,
            String timing,
            String event,
            String statement) {
        this(schema, tableName, name, timing, event, statement, null);
    }
}
