package io.github.migraphe.postgresql;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.graph.MigrationNode;
import io.github.migraphe.api.graph.NodeId;
import io.github.migraphe.api.spi.MigrationNodeProvider;
import io.github.migraphe.api.spi.SqlDefinition;
import io.github.migraphe.api.spi.TaskDefinition;
import java.util.Set;

/** PostgreSQL MigrationNode を生成する Provider。 */
public final class PostgreSQLMigrationNodeProvider implements MigrationNodeProvider {

    @Override
    public MigrationNode createNode(
            NodeId nodeId, TaskDefinition task, Set<NodeId> dependencies, Environment environment) {

        if (!(environment instanceof PostgreSQLEnvironment)) {
            throw new PostgreSQLException(
                    "Environment must be PostgreSQLEnvironment, got: "
                            + environment.getClass().getName());
        }

        PostgreSQLEnvironment pgEnv = (PostgreSQLEnvironment) environment;

        // UP SQL を取得
        String upSql = resolveSql(task.up(), nodeId, "up");

        // DOWN SQL を取得（オプション）
        String downSql = null;
        if (task.down().isPresent() && task.down().get().isDefined()) {
            downSql = resolveSql(task.down().get(), nodeId, "down");
        }

        // PostgreSQLMigrationNode を構築
        var builder =
                PostgreSQLMigrationNode.builder()
                        .id(nodeId)
                        .name(task.name())
                        .environment(pgEnv)
                        .dependencies(dependencies)
                        .upSql(upSql);

        task.description().ifPresent(builder::description);

        if (downSql != null && !downSql.isBlank()) {
            builder.downSql(downSql);
        }

        return builder.build();
    }

    /**
     * SqlDefinition から SQL 文字列を解決する。
     *
     * @param sqlDef SQL 定義
     * @param nodeId ノードID（エラーメッセージ用）
     * @param direction "up" or "down"（エラーメッセージ用）
     * @return SQL 文字列
     */
    private String resolveSql(SqlDefinition sqlDef, NodeId nodeId, String direction) {
        // sql が直接指定されている場合
        if (sqlDef.sql().isPresent()) {
            return sqlDef.sql().get();
        }

        // file または resource の読み込みは将来対応
        // 現時点では sql のみサポート
        if (sqlDef.file().isPresent()) {
            throw new PostgreSQLException(
                    "File-based SQL loading not yet supported for node: "
                            + nodeId.value()
                            + " ("
                            + direction
                            + ")");
        }

        if (sqlDef.resource().isPresent()) {
            throw new PostgreSQLException(
                    "Resource-based SQL loading not yet supported for node: "
                            + nodeId.value()
                            + " ("
                            + direction
                            + ")");
        }

        throw new PostgreSQLException(
                "No SQL defined for node: " + nodeId.value() + " (" + direction + ")");
    }
}
