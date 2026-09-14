package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.api.target.Target;
import java.util.Set;

/**
 * {@link MigrationNodeProvider} that assembles a {@link JdbcMigrationNode} from configuration.
 *
 * <p>Returned by {@link JdbcPlugin#migrationNodeProvider()} and invoked by the core configuration
 * layer for each task YAML. It translates a {@link SqlTaskDefinition} (UP/DOWN SQL plus the {@code
 * autocommit} settings) and the resolved dependency set into an immutable migration node. A blank
 * {@code down} SQL is treated as absent so the resulting node has no rollback task.
 *
 * <p>{@code autocommit} may be written as a bare boolean or as {@code autocommit: {up, down}}, and
 * the two forms may be mixed — a direction the task named wins over the bare value, which in turn
 * wins over the default of {@code false}. Resolving that here rather than in {@link
 * SqlTaskDefinition} keeps the precedence in one place: SmallRye reports all three keys
 * independently and takes no view on which should win.
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
     * @param target the target the node runs against; must be a {@link JdbcTarget}
     * @return a new {@link JdbcMigrationNode}
     * @throws JdbcException if {@code target} is not a {@link JdbcTarget} or {@code task} is not a
     *     {@link SqlTaskDefinition}
     */
    @Override
    public MigrationNode createNode(
            NodeId nodeId, TaskDefinition<String> task, Set<NodeId> dependencies, Target target) {

        if (!(target instanceof JdbcTarget jdbcEnv)) {
            throw new JdbcException(
                    "Target must be JdbcTarget, got: " + target.getClass().getName());
        }

        if (!(task instanceof SqlTaskDefinition sqlTask)) {
            throw new JdbcException(
                    "TaskDefinition must be SqlTaskDefinition, got: " + task.getClass().getName());
        }

        String upSql = task.up();
        boolean autocommit = sqlTask.autocommit().orElse(false);
        boolean autocommitUp = sqlTask.autocommitUp().orElse(autocommit);
        boolean autocommitDown = sqlTask.autocommitDown().orElse(autocommit);

        var builder =
                JdbcMigrationNode.builder()
                        .id(nodeId)
                        .name(task.name())
                        .target(jdbcEnv)
                        .dependencies(dependencies)
                        .upSql(upSql)
                        .autocommitUp(autocommitUp)
                        .autocommitDown(autocommitDown);

        task.description().ifPresent(builder::description);
        task.down().filter(sql -> !sql.isBlank()).ifPresent(builder::downSql);
        sqlTask.noWayBack().filter(reason -> !reason.isBlank()).ifPresent(builder::noWayBack);

        return builder.build();
    }
}
