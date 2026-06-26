package io.github.kakusuke.migraphe.postgresql.schema;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Schema information for a single PostgreSQL row-level security (RLS) policy.
 *
 * <p>An RLS policy restricts which rows of a table a role may read or write, optionally enforcing a
 * {@code USING} expression for visibility and a {@code WITH CHECK} expression for new or updated
 * rows. The values are read from the {@code pg_policy} catalog, with the command decoded from
 * {@code polcmd} and the expressions rendered by {@code pg_get_expr}. This record is one of the
 * PostgreSQL-specific elements collected by {@link PostgreSQLSchemaInfoProvider} and exposed
 * through {@link PostgreSQLSchemaInfo}.
 *
 * @param name the policy name
 * @param schema the schema of the table the policy applies to
 * @param tableName the name of the table the policy applies to
 * @param command the command the policy governs: {@code "SELECT"}, {@code "INSERT"}, {@code
 *     "UPDATE"}, {@code "DELETE"}, or {@code "ALL"}
 * @param roles the roles the policy applies to (may be empty when the applicable roles are not
 *     captured)
 * @param usingExpression the {@code USING} expression controlling row visibility, or {@code null}
 *     if the policy has none
 * @param withCheckExpression the {@code WITH CHECK} expression validating new or updated rows, or
 *     {@code null} if the policy has none
 */
public record PostgreSQLPolicyInfo(
        String name,
        String schema,
        String tableName,
        String command,
        List<String> roles,
        @Nullable String usingExpression,
        @Nullable String withCheckExpression) {}
