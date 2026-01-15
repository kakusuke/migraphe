package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.Task;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.jspecify.annotations.Nullable;

/** PostgreSQL マイグレーションノードの実装。 Builder パターンで SQL ファイルまたは文字列から構築する。 */
public final class PostgreSQLMigrationNode implements MigrationNode {

    private final NodeId id;
    private final String name;
    private final @Nullable String description;
    private final PostgreSQLEnvironment environment;
    private final Set<NodeId> dependencies;
    private final String upSql;
    private final @Nullable String downSql;
    private final boolean autocommit;

    private PostgreSQLMigrationNode(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.description = builder.description;
        this.environment =
                Objects.requireNonNull(builder.environment, "environment must not be null");
        this.dependencies = Set.copyOf(builder.dependencies);
        this.upSql = Objects.requireNonNull(builder.upSql, "upSql must not be null");
        this.downSql = builder.downSql;
        this.autocommit = builder.autocommit;

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
    public @Nullable String description() {
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
        return PostgreSQLUpTask.create(environment, upSql, downSql, autocommit);
    }

    @Override
    public @Nullable Task downTask() {
        if (downSql != null) {
            return PostgreSQLDownTask.create(environment, downSql, autocommit);
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private @Nullable NodeId id;
        private @Nullable String name;
        private @Nullable String description;
        private @Nullable PostgreSQLEnvironment environment;
        private Set<NodeId> dependencies = Set.of();
        private @Nullable String upSql;
        private @Nullable String downSql;
        private boolean autocommit = false;

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

        public Builder description(@Nullable String description) {
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
        public Builder downSql(@Nullable String sql) {
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

        /**
         * autocommit モードを設定する。
         *
         * <p>true の場合、トランザクションを使用せずに実行する。 CREATE DATABASE などトランザクション内で実行できない SQL 用。
         *
         * @param autocommit autocommit を有効にする場合は true
         * @return Builder
         */
        public Builder autocommit(boolean autocommit) {
            this.autocommit = autocommit;
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
