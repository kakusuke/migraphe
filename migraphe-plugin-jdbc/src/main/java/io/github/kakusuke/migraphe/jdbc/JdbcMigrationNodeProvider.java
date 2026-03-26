package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import java.util.Set;

/** 汎用 JDBC MigrationNode を生成する Provider。 */
public final class JdbcMigrationNodeProvider implements MigrationNodeProvider<String> {

    @Override
    public MigrationNode createNode(
            NodeId nodeId,
            TaskDefinition<String> task,
            Set<NodeId> dependencies,
            Environment environment) {

        if (!(environment instanceof JdbcEnvironment jdbcEnv)) {
            throw new JdbcException(
                    "Environment must be JdbcEnvironment, got: "
                            + environment.getClass().getName());
        }

        if (!(task instanceof SqlTaskDefinition sqlTask)) {
            throw new JdbcException(
                    "TaskDefinition must be SqlTaskDefinition, got: " + task.getClass().getName());
        }

        String upSql = task.up();
        boolean autocommit = sqlTask.autocommit().orElse(false);

        var builder =
                JdbcMigrationNode.builder()
                        .id(nodeId)
                        .name(task.name())
                        .environment(jdbcEnv)
                        .dependencies(dependencies)
                        .upSql(upSql)
                        .autocommit(autocommit);

        task.description().ifPresent(builder::description);
        task.down().filter(sql -> !sql.isBlank()).ifPresent(builder::downSql);

        return builder.build();
    }
}
