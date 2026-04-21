package io.github.kakusuke.migraphe.jdbc.schema;

/** トリガー情報。 */
public record JdbcTriggerInfo(
        String name, String tableName, String event, String timing, String body) {}
