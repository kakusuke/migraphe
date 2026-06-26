package io.github.kakusuke.migraphe.jdbc.schema;

/**
 * Trigger information.
 *
 * <p>Immutable data holder describing a database trigger. The portable JDBC metadata API does not
 * expose triggers; this record is populated by dialect-specific providers such as PostgreSQL and
 * MySQL.
 *
 * @param name the trigger name
 * @param tableName the name of the table the trigger is attached to
 * @param event the triggering event (for example {@code "INSERT"}, {@code "UPDATE"}, {@code
 *     "DELETE"})
 * @param timing when the trigger fires relative to the event (for example {@code "BEFORE"}, {@code
 *     "AFTER"})
 * @param body the trigger body or action definition
 */
public record JdbcTriggerInfo(
        String name, String tableName, String event, String timing, String body) {}
