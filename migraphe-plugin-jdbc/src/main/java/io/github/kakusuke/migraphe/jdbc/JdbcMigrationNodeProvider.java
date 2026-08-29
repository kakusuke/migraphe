package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import java.util.Set;

/**
 * {@link MigrationNodeProvider} that assembles a {@link JdbcMigrationNode} from configuration.
 *
 * <p>Returned by {@link JdbcPlugin#migrationNodeProvider()} and invoked by the core configuration
 * layer for each task YAML. It translates a {@link SqlTaskDefinition} (UP/DOWN SQL plus the {@code
 * autocommit} flag) and the resolved dependency set into an immutable migration node. A blank
 * {@code down} SQL is treated as absent so the resulting node has no rollback task.
 */
public final class JdbcMigrationNodeProvider implements MigrationNodeProvider<String> {

    /** Creates a new {@code JdbcMigrationNodeProvider}. */
    public JdbcMigrationNodeProvider() {}

    /**
     * Builds a {@link JdbcMigrationNode} from a SQL task definition.
     *
     * @param nodeId the identifier to assign to the node
     * @param task the mapped task configuration; must be a {@link SqlTaskDefinition}
     * @param dependencies the resolved node identifiers this node depends on
     * @param environment the environment the node runs against; must be a {@link JdbcEnvironment}
     * @return a new {@link JdbcMigrationNode}
     * @throws JdbcException if {@code environment} is not a {@link JdbcEnvironment} or {@code task}
     *     is not a {@link SqlTaskDefinition}
     */
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
        sqlTask.noWayBack().filter(reason -> !reason.isBlank()).ifPresent(builder::noWayBack);

        return builder.build();
    }
}
