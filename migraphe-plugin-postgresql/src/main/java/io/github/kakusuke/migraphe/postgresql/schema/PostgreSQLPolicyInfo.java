package io.github.kakusuke.migraphe.postgresql.schema;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record PostgreSQLPolicyInfo(
        String name,
        String schema,
        String tableName,
        String command,
        List<String> roles,
        @Nullable String usingExpression,
        @Nullable String withCheckExpression) {}
