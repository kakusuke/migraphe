package io.github.kakusuke.migraphe.postgresql.schema;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record PostgreSQLEnumInfo(String name, List<String> labels, @Nullable String owner) {

    public PostgreSQLEnumInfo(String name, List<String> labels) {
        this(name, labels, null);
    }
}
