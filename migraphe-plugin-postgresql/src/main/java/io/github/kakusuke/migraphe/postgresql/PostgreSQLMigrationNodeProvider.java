package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.jdbc.JdbcMigrationNode;
import io.github.kakusuke.migraphe.jdbc.SqlTaskDefinition;
import java.util.Set;

/**
 * {@link MigrationNodeProvider} that builds PostgreSQL migration nodes.
 *
 * <p>The UP/DOWN actions of a {@link TaskDefinition} are SQL strings ({@code String}), so this
 * provider is parameterized as {@code MigrationNodeProvider<String>}. It delegates to the generic
 * {@link JdbcMigrationNode} builder; only the type checks and PostgreSQL environment binding are
 * PostgreSQL-specific. Registered through {@link PostgreSQLPlugin}.
 */
public final class PostgreSQLMigrationNodeProvider implements MigrationNodeProvider<String> {

    /** Creates a new {@code PostgreSQLMigrationNodeProvider}. */
    public PostgreSQLMigrationNodeProvider() {}

    /**
     * Builds a {@link JdbcMigrationNode} for the given task and dependencies.
     *
     * <p>The task's optional description and (non-blank) DOWN SQL are applied when present; {@code
     * autocommit} defaults to {@code false} when not specified on the {@link SqlTaskDefinition}.
     *
     * @param nodeId the unique identifier of the node
     * @param task the task definition supplying name, UP/DOWN SQL, description, and autocommit;
     *     must be a {@link SqlTaskDefinition}
     * @param dependencies the set of node ids this node depends on
     * @param environment the environment to run against; must be a {@link PostgreSQLEnvironment}
     * @return the constructed {@link MigrationNode}
     * @throws PostgreSQLException if {@code environment} is not a {@link PostgreSQLEnvironment} or
     *     {@code task} is not a {@link SqlTaskDefinition}
     */
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

        String upSql = task.up();
        boolean autocommit = sqlTask.autocommit().orElse(false);

        var builder =
                JdbcMigrationNode.builder()
                        .id(nodeId)
                        .name(task.name())
                        .environment(pgEnv)
                        .dependencies(dependencies)
                        .upSql(upSql)
                        .autocommit(autocommit);

        task.description().ifPresent(builder::description);
        task.down().filter(sql -> !sql.isBlank()).ifPresent(builder::downSql);

        return builder.build();
    }
}
