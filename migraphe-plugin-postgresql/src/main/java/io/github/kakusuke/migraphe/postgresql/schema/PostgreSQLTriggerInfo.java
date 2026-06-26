package io.github.kakusuke.migraphe.postgresql.schema;

import java.util.List;

/**
 * Schema information for a single PostgreSQL trigger.
 *
 * <p>A trigger binds a function to a table so that the function runs automatically in response to
 * data-modification events. The values are decoded from the {@code pg_trigger} catalog: the timing
 * and event set are derived from the {@code tgtype} bitmask, and internal/system triggers are
 * excluded. This record is one of the PostgreSQL-specific elements collected by {@link
 * PostgreSQLSchemaInfoProvider} and exposed through {@link PostgreSQLSchemaInfo}.
 *
 * @param name the trigger name
 * @param schema the schema of the table the trigger is attached to
 * @param tableName the name of the table the trigger is attached to
 * @param timing when the trigger fires relative to the event: {@code "BEFORE"}, {@code "AFTER"}, or
 *     {@code "INSTEAD OF"}
 * @param events the events that fire the trigger, drawn from {@code "INSERT"}, {@code "UPDATE"},
 *     {@code "DELETE"}, and {@code "TRUNCATE"}
 * @param functionName the name of the function the trigger invokes
 * @param isConstraint {@code true} if the trigger is a constraint trigger (its {@code
 *     pg_trigger.tgconstraint} catalog column references a constraint), {@code false} otherwise
 */
public record PostgreSQLTriggerInfo(
        String name,
        String schema,
        String tableName,
        String timing,
        List<String> events,
        String functionName,
        boolean isConstraint) {}
