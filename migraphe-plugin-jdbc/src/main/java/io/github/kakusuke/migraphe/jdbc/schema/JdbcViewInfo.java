package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/**
 * Information about a single database view.
 *
 * <p>Populated from {@link java.sql.DatabaseMetaData} for tables of type {@code "VIEW"}. The
 * generic JDBC provider does not retrieve the view's defining SQL, so {@code definition} is an
 * empty string unless supplied by a dialect-specific provider.
 *
 * @param name the view name (the {@code TABLE_NAME} value from {@link
 *     java.sql.DatabaseMetaData#getTables})
 * @param remarks the comment/description on the view ({@code REMARKS}); empty string when none
 * @param columns the columns exposed by the view, in ordinal order
 * @param definition the SQL text that defines the view; empty string when not available
 */
public record JdbcViewInfo(
        String name, String remarks, List<JdbcColumnInfo> columns, String definition) {}
