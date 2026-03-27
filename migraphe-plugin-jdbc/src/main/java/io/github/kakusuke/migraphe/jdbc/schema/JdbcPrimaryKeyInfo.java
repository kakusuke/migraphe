package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/** 主キー情報。 */
public record JdbcPrimaryKeyInfo(String name, List<String> columns) {}
