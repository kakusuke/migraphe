package io.github.migraphe.postgresql;

import io.github.migraphe.core.environment.Environment;
import io.github.migraphe.core.graph.MigrationNode;
import io.github.migraphe.core.graph.NodeId;
import io.github.migraphe.core.task.Task;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** PostgreSQL マイグレーションノードの実装。 Builder パターンで SQL ファイルまたは文字列から構築する。 */
public final class PostgreSQLMigrationNode implements MigrationNode {

    private final NodeId id;
    private final String name;
    private final String description;
    private final PostgreSQLEnvironment environment;
    private final Set<NodeId> dependencies;
    private final String upSql;
    private final Optional<String> downSql;

    private PostgreSQLMigrationNode(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.description = builder.description != null ? builder.description : "";
        this.environment =
                Objects.requireNonNull(builder.environment, "environment must not be null");
        this.dependencies = Set.copyOf(builder.dependencies);
        this.upSql = Objects.requireNonNull(builder.upSql, "upSql must not be null");
        this.downSql = Optional.ofNullable(builder.downSql);

        if (upSql.isBlank()) {
            throw new IllegalArgumentException("upSql must not be blank");
        }
    }

    @Override
    public NodeId id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Environment environment() {
        return environment;
    }

    @Override
    public Set<NodeId> dependencies() {
        return dependencies;
    }

    @Override
    public Task upTask() {
        return PostgreSQLUpTask.create(environment, upSql, downSql);
    }

    @Override
    public Optional<Task> downTask() {
        return downSql.map(sql -> PostgreSQLDownTask.create(environment, sql));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private NodeId id;
        private String name;
        private String description;
        private PostgreSQLEnvironment environment;
        private Set<NodeId> dependencies = Set.of();
        private String upSql;
        private String downSql;

        public Builder id(String id) {
            this.id = NodeId.of(id);
            return this;
        }

        public Builder id(NodeId id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder environment(PostgreSQLEnvironment environment) {
            this.environment = environment;
            return this;
        }

        public Builder dependencies(Set<NodeId> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public Builder dependencies(NodeId... dependencies) {
            this.dependencies = Set.of(dependencies);
            return this;
        }

        /**
         * UP SQL を文字列として設定する。
         *
         * @param sql UP SQL
         * @return Builder
         */
        public Builder upSql(String sql) {
            this.upSql = sql;
            return this;
        }

        /**
         * UP SQL をファイルから読み込む。
         *
         * @param path SQL ファイルのパス
         * @return Builder
         * @throws IOException ファイル読み込みに失敗した場合
         */
        public Builder upSqlFromFile(Path path) throws IOException {
            this.upSql = Files.readString(path);
            return this;
        }

        /**
         * UP SQL をリソースから読み込む。
         *
         * @param resourcePath リソースパス（例: "/migrations/V001__create_table.sql"）
         * @return Builder
         * @throws IOException リソース読み込みに失敗した場合
         */
        public Builder upSqlFromResource(String resourcePath) throws IOException {
            this.upSql = loadResource(resourcePath);
            return this;
        }

        /**
         * DOWN SQL を文字列として設定する。
         *
         * @param sql DOWN SQL
         * @return Builder
         */
        public Builder downSql(String sql) {
            this.downSql = sql;
            return this;
        }

        /**
         * DOWN SQL をファイルから読み込む。
         *
         * @param path SQL ファイルのパス
         * @return Builder
         * @throws IOException ファイル読み込みに失敗した場合
         */
        public Builder downSqlFromFile(Path path) throws IOException {
            this.downSql = Files.readString(path);
            return this;
        }

        /**
         * DOWN SQL をリソースから読み込む。
         *
         * @param resourcePath リソースパス
         * @return Builder
         * @throws IOException リソース読み込みに失敗した場合
         */
        public Builder downSqlFromResource(String resourcePath) throws IOException {
            this.downSql = loadResource(resourcePath);
            return this;
        }

        public PostgreSQLMigrationNode build() {
            return new PostgreSQLMigrationNode(this);
        }

        private String loadResource(String resourcePath) throws IOException {
            try (var is = getClass().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    throw new IOException("Resource not found: " + resourcePath);
                }
                return new String(is.readAllBytes());
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PostgreSQLMigrationNode other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "PostgreSQLMigrationNode{"
                + "id="
                + id
                + ", name='"
                + name
                + "', environment="
                + environment.name()
                + ", dependencies="
                + dependencies.size()
                + '}';
    }
}
