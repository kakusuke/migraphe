package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/** ビュー情報。 */
public record JdbcViewInfo(
        String name, String remarks, List<JdbcColumnInfo> columns, String definition) {}
