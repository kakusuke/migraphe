package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/** インデックス情報。 */
public record JdbcIndexInfo(String name, boolean unique, List<JdbcIndexColumn> columns) {}
