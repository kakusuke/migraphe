package io.github.kakusuke.migraphe.jdbc.schema;

/**
 * The kind of a stored routine in a database schema.
 *
 * <p>Distinguishes the two categories of routine metadata that dialect-specific schema providers
 * may collect.
 */
public enum RoutineType {
    /** A stored procedure (invoked with {@code CALL}, does not necessarily return a value). */
    PROCEDURE,
    /** A stored function (returns a value and can be used within SQL expressions). */
    FUNCTION
}
