package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.jdbc.JdbcMigrationNode;
import io.github.kakusuke.migraphe.jdbc.SqlTaskDefinition;
import java.util.Set;

/**
 * MySQL MigrationNode を生成する Provider。
 *
 * <p>TaskDefinition の UP/DOWN は SQL 文字列（String）。
 */
public final class MySQLMigrationNodeProvider implements MigrationNodeProvider<String> {

    @Override
    public MigrationNode createNode(
            NodeId nodeId,
            TaskDefinition<String> task,
            Set<NodeId> dependencies,
            Environment environment) {

        if (!(environment instanceof MySQLEnvironment mysqlEnv)) {
            throw new MySQLException(
                    "Environment must be MySQLEnvironment, got: "
                            + environment.getClass().getName());
        }

        if (!(task instanceof SqlTaskDefinition sqlTask)) {
            throw new MySQLException(
                    "TaskDefinition must be SqlTaskDefinition, got: " + task.getClass().getName());
        }

        String upSql = task.up();
        boolean autocommit = sqlTask.autocommit().orElse(false);

        var builder =
                JdbcMigrationNode.builder()
                        .id(nodeId)
                        .name(task.name())
                        .environment(mysqlEnv)
                        .dependencies(dependencies)
                        .upSql(upSql)
                        .autocommit(autocommit);

        task.description().ifPresent(builder::description);
        task.down().filter(sql -> !sql.isBlank()).ifPresent(builder::downSql);

        return builder.build();
    }
}
