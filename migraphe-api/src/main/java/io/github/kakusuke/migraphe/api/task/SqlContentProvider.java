package io.github.kakusuke.migraphe.api.task;

/**
 * A capability interface for {@link Task tasks} that can expose the SQL they execute.
 *
 * <p>Implemented by SQL-based tasks so that tooling can surface the underlying statement, for
 * example when rendering a failure with the offending SQL. Migraphe checks for this capability via
 * {@code instanceof} and ignores tasks that do not implement it.
 *
 * @see Task
 */
public interface SqlContentProvider {

    /**
     * Returns the SQL content this task executes.
     *
     * @return the SQL statement(s) that the task runs
     */
    String sqlContent();
}
