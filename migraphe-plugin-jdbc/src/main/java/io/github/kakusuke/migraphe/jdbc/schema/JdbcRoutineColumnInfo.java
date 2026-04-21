package io.github.kakusuke.migraphe.jdbc.schema;

/** ルーチンパラメータ情報。 */
public record JdbcRoutineColumnInfo(String name, String typeName, ColumnType columnType) {}
