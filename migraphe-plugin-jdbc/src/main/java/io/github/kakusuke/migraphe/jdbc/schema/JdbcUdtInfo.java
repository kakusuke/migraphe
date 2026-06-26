package io.github.kakusuke.migraphe.jdbc.schema;

/**
 * User-defined type (UDT) information.
 *
 * <p>Immutable data holder describing a user-defined type, corresponding to a row from {@link
 * java.sql.DatabaseMetaData#getUDTs}.
 *
 * @param name the type name
 * @param className the fully qualified name of the Java class to which the type maps
 * @param dataType the JDBC type code (a {@link java.sql.Types} constant) identifying the UDT
 *     category (for example {@code STRUCT}, {@code DISTINCT}, {@code JAVA_OBJECT})
 * @param remarks the type's description or comment, or an empty string if none
 */
public record JdbcUdtInfo(String name, String className, int dataType, String remarks) {}
