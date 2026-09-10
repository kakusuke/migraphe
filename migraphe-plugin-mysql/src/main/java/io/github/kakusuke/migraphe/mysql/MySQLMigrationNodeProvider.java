package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.jdbc.JdbcMigrationNode;
import io.github.kakusuke.migraphe.jdbc.SqlTaskDefinition;
import java.util.Set;

/**
 * {@link MigrationNodeProvider} that constructs MySQL {@link MigrationNode} instances.
 *
 * <p>Returned by {@link MySQLPlugin#migrationNodeProvider()}, this provider builds {@link
 * JdbcMigrationNode}s wired to a {@link MySQLTarget}. The task's UP and DOWN actions are SQL
 * strings ({@code String}), supplied as a {@link SqlTaskDefinition}. A blank DOWN action is treated
 * as "no rollback" and is not attached to the node.
 *
 * @see MySQLPlugin
 * @see JdbcMigrationNode
 * @see SqlTaskDefinition
 */
public final class MySQLMigrationNodeProvider implements MigrationNodeProvider<String> {

    /** Creates a new {@code MySQLMigrationNodeProvider}. */
    public MySQLMigrationNodeProvider() {}

    /**
     * Builds a MySQL {@link MigrationNode} from a SQL task definition.
     *
     * @param nodeId the unique identifier to assign to the created node
     * @param task the task definition; must be a {@link SqlTaskDefinition} carrying SQL strings
     * @param dependencies the IDs of the nodes this node depends on, already resolved by the
     *     framework
     * @param target the target this node belongs to; must be a {@link MySQLTarget}
     * @return the constructed {@link JdbcMigrationNode}
     * @throws MySQLException if {@code target} is not a {@link MySQLTarget} or {@code task} is not
     *     a {@link SqlTaskDefinition}
     */
    @Override
    public MigrationNode createNode(
            NodeId nodeId, TaskDefinition<String> task, Set<NodeId> dependencies, Target target) {

        if (!(target instanceof MySQLTarget mysqlEnv)) {
            throw new MySQLException(
                    "Target must be MySQLTarget, got: " + target.getClass().getName());
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
                        .target(mysqlEnv)
                        .dependencies(dependencies)
                        .upSql(upSql)
                        .autocommit(autocommit);

        task.description().ifPresent(builder::description);
        task.down().filter(sql -> !sql.isBlank()).ifPresent(builder::downSql);

        return builder.build();
    }
}
