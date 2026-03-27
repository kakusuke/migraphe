package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/** ストアドプロシージャまたはファンクション情報。 */
public record JdbcRoutineInfo(
        String name,
        RoutineType type,
        String remarks,
        String body,
        List<JdbcRoutineColumnInfo> parameters) {}
