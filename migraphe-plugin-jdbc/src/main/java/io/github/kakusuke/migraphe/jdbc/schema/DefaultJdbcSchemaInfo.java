package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/** デフォルト実装の JDBC スキーマ情報レコード。 */
public record DefaultJdbcSchemaInfo(List<JdbcSchemaDetail> schemas) implements JdbcSchemaInfo {}
