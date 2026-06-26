package io.github.kakusuke.migraphe.mysql.schema;

/**
 * A single MySQL storage engine and its capability flags, as reported by {@code
 * information_schema.ENGINES}.
 *
 * <p>This data holder mirrors one row of the {@code ENGINES} table and is collected into {@link
 * MySQLSchemaInfo#storageEngines()} so generators can document which engines the server supports.
 *
 * @param name the engine name (the {@code ENGINE} column, for example {@code "InnoDB"} or {@code
 *     "MyISAM"})
 * @param support the server's support level for the engine (the {@code SUPPORT} column, for example
 *     {@code "DEFAULT"}, {@code "YES"}, or {@code "NO"})
 * @param transactions whether the engine supports transactions (the {@code TRANSACTIONS} column,
 *     typically {@code "YES"} or {@code "NO"}); empty string if the source value was {@code null}
 * @param xa whether the engine supports XA distributed transactions (the {@code XA} column); empty
 *     string if the source value was {@code null}
 * @param savepoints whether the engine supports savepoints (the {@code SAVEPOINTS} column); empty
 *     string if the source value was {@code null}
 */
public record MySQLStorageEngineInfo(
        String name, String support, String transactions, String xa, String savepoints) {}
