package io.github.kakusuke.migraphe.jdbc.schema;

import org.jspecify.annotations.Nullable;

/** テーブルまたはビューのカラム情報。 */
public record JdbcColumnInfo(
        String name,
        String typeName,
        int dataType,
        int size,
        int decimalDigits,
        boolean nullable,
        @Nullable String defaultValue,
        boolean autoIncrement,
        boolean generated,
        @Nullable String remarks,
        int ordinalPosition) {}
