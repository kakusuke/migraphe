package io.github.migraphe.postgresql;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.graph.MigrationNode;
import io.github.migraphe.api.graph.NodeId;
import io.github.migraphe.api.spi.MigrationNodeProvider;
import io.github.migraphe.api.spi.TaskDefinition;
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

        if (!(environment instanceof PostgreSQLEnvironment)) {
            throw new PostgreSQLException(
                    "Environment must be PostgreSQLEnvironment, got: "
                            + environment.getClass().getName());
        }

        PostgreSQLEnvironment pgEnv = (PostgreSQLEnvironment) environment;

        // UP SQL を取得
        String upSql = task.up();

        // PostgreSQLMigrationNode を構築
        var builder =
                PostgreSQLMigrationNode.builder()
                        .id(nodeId)
                        .name(task.name())
                        .environment(pgEnv)
                        .dependencies(dependencies)
                        .upSql(upSql);

        // description（オプション）
        task.description().ifPresent(builder::description);

        // DOWN SQL（オプション）
        task.down().filter(sql -> !sql.isBlank()).ifPresent(builder::downSql);

        return builder.build();
    }
}
