package io.github.kakusuke.migraphe.postgresql.schema;

import java.util.List;

public record PostgreSQLEnumInfo(String name, List<String> labels) {}
