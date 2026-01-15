package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import java.util.Set;

/**
 * PostgreSQL MigrationNode を生成する Provider。
 *
 * <p>TaskDefinition の UP/DOWN は SQL 文字列（String）。
 */
public final class PostgreSQLMigrationNodeProvider implements MigrationNodeProvider<String> {

    @Override
    public MigrationNode createNode(
            NodeId nodeId,
            TaskDefinition<String> task,
            Set<NodeId> dependencies,
            Environment environment) {

        if (!(environment instanceof PostgreSQLEnvironment pgEnv)) {
            throw new PostgreSQLException(
                    "Environment must be PostgreSQLEnvironment, got: "
                            + environment.getClass().getName());
        }

        if (!(task instanceof SqlTaskDefinition sqlTask)) {
            throw new PostgreSQLException(
                    "TaskDefinition must be SqlTaskDefinition, got: " + task.getClass().getName());
        }

        // UP SQL を取得
        String upSql = task.up();

        // autocommit を取得（デフォルト false）
        boolean autocommit = sqlTask.autocommit().orElse(false);

        // PostgreSQLMigrationNode を構築
        var builder =
                PostgreSQLMigrationNode.builder()
                        .id(nodeId)
                        .name(task.name())
                        .environment(pgEnv)
                        .dependencies(dependencies)
                        .upSql(upSql)
                        .autocommit(autocommit);

        // description（オプション）
        task.description().ifPresent(builder::description);

        // DOWN SQL（オプション）
        task.down().filter(sql -> !sql.isBlank()).ifPresent(builder::downSql);

        return builder.build();
    }
}
