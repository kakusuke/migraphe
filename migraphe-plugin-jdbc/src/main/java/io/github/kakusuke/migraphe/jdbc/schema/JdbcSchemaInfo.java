package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/** JDBC データベーススキーマ情報のトップレベルコンテナ。 */
public interface JdbcSchemaInfo {
    List<JdbcSchemaDetail> schemas();
}
