package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/** スキーマの詳細情報。 */
public record JdbcSchemaDetail(
        String name,
        List<JdbcTableInfo> tables,
        List<JdbcViewInfo> views,
        List<JdbcRoutineInfo> routines,
        List<JdbcTriggerInfo> triggers,
        List<JdbcSequenceInfo> sequences,
        List<JdbcUdtInfo> udts) {}
