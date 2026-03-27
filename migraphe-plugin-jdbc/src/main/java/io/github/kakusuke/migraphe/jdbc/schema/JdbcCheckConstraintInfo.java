package io.github.kakusuke.migraphe.jdbc.schema;

/** チェック制約情報。 */
public record JdbcCheckConstraintInfo(String name, String checkClause) {}
