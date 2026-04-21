package io.github.kakusuke.migraphe.jdbc.schema;

/** ユーザー定義型（UDT）情報。 */
public record JdbcUdtInfo(String name, String className, int dataType, String remarks) {}
