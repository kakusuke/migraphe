package io.github.migraphe.postgresql;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.graph.MigrationNode;
import io.github.migraphe.api.graph.NodeId;
import io.github.migraphe.api.spi.MigrationNodeProvider;
import java.util.List;
import java.util.Map;

/** PostgreSQL MigrationNode を生成する Provider。 */
public final class PostgreSQLMigrationNodeProvider implements MigrationNodeProvider {

    @Override
    public MigrationNode createNode(
            NodeId nodeId, Map<String, Object> taskConfig, Environment environment) {

        if (!(environment instanceof PostgreSQLEnvironment)) {
            throw new PostgreSQLException(
                    "Environment must be PostgreSQLEnvironment, got: "
                            + environment.getClass().getName());
        }

        PostgreSQLEnvironment pgEnv = (PostgreSQLEnvironment) environment;

        // タスク設定から情報を取得
        String name = getRequiredString(taskConfig, "name", nodeId);
        String description = getString(taskConfig, "description", "");

        // 依存関係を取得
        @SuppressWarnings("unchecked")
        List<String> dependencyStrings =
                (List<String>) taskConfig.getOrDefault("dependencies", List.of());
        NodeId[] dependencies = dependencyStrings.stream().map(NodeId::of).toArray(NodeId[]::new);

        // UP/DOWN SQL を取得
        Map<String, Object> upBlock = getRequiredMap(taskConfig, "up", nodeId);
        String upSql = getRequiredString(upBlock, "sql", nodeId);

        // DOWN は optional
        Map<String, Object> downBlock = getMap(taskConfig, "down");
        String downSql = downBlock != null ? getString(downBlock, "sql", null) : null;

        // PostgreSQLMigrationNode を構築
        var builder =
                PostgreSQLMigrationNode.builder()
                        .id(nodeId.value())
                        .name(name)
                        .environment(pgEnv)
                        .dependencies(dependencies)
                        .upSql(upSql);

        if (description != null && !description.isBlank()) {
            builder.description(description);
        }

        if (downSql != null && !downSql.isBlank()) {
            builder.downSql(downSql);
        }

        return builder.build();
    }

    // ========== ヘルパーメソッド ==========

    private String getRequiredString(Map<String, Object> map, String key, NodeId nodeId) {
        Object value = map.get(key);
        if (value == null) {
            throw new PostgreSQLException(
                    "Missing required field '" + key + "' for node: " + nodeId.value());
        }
        return value.toString();
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getRequiredMap(Map<String, Object> map, String key, NodeId nodeId) {
        Object value = map.get(key);
        if (value == null) {
            throw new PostgreSQLException(
                    "Missing required field '" + key + "' for node: " + nodeId.value());
        }
        if (!(value instanceof Map)) {
            throw new PostgreSQLException(
                    "Field '" + key + "' must be a map for node: " + nodeId.value());
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map)) {
            throw new PostgreSQLException("Field '" + key + "' must be a map");
        }
        return (Map<String, Object>) value;
    }
}
