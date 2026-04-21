package io.github.kakusuke.migraphe.postgresql.schema;

import org.jspecify.annotations.Nullable;

public record PostgreSQLExtensionInfo(String name, String version, @Nullable String owner) {

    public PostgreSQLExtensionInfo(String name, String version) {
        this(name, version, null);
    }
}
