package io.github.kakusuke.migraphe.jdbc;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.Task;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * JDBC implementation of {@link MigrationNode}, built through its {@link Builder} from SQL strings,
 * files, or classpath resources.
 *
 * <p>A node ties together an identifier, an owning {@link JdbcEnvironment}, its dependency set, and
 * the UP/DOWN SQL plus the {@code autocommit} flag. It is an immutable structural record of the
 * migration; the actual execution logic is produced on demand by {@link #upTask()} (always present)
 * and {@link #downTask()} (present only when {@code downSql} was supplied). Node identity is
 * defined solely by {@link #id()} for use in the migration graph.
 */
public final class JdbcMigrationNode implements MigrationNode {

    private final NodeId id;
    private final String name;
    private final @Nullable String description;
    private final JdbcEnvironment environment;
    private final Set<NodeId> dependencies;
    private final String upSql;
    private final @Nullable String downSql;
    private final boolean autocommit;

    private JdbcMigrationNode(Builder builder) {
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

    /**
     * Returns the SHA-256 of this node's UP SQL, hex-encoded.
     *
     * <p>Normalization is deliberately limited to line endings and the surrounding whitespace a
     * YAML block scalar contributes, so the same migration checked out on another platform keeps
     * its fingerprint. It stops there on purpose: collapsing interior whitespace would change the
     * meaning of string literals, and broadening normalization later invalidates every fingerprint
     * already recorded.
     *
     * @return the hex-encoded SHA-256 of the normalized UP SQL
     */
    @Override
    public String fingerprint() {
        String normalized = upSql.replace("\r\n", "\n").replace("\r", "\n").strip();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
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

    /**
     * Creates the forward task that applies this node's UP SQL.
     *
     * @return a {@link JdbcUpTask} carrying the UP SQL, optional rollback SQL, and autocommit flag
     */
    @Override
    public Task upTask() {
        return JdbcUpTask.create(environment, upSql, downSql, autocommit);
    }

    /**
     * Creates the rollback task that applies this node's DOWN SQL, if any.
     *
     * @return a {@link JdbcDownTask} when {@code downSql} was supplied, or {@code null} when the
     *     node is not reversible
     */
    @Override
    public @Nullable Task downTask() {
        if (downSql != null) {
            return JdbcDownTask.create(environment, downSql, autocommit);
        }
        return null;
    }

    /**
     * Returns a new builder for {@link JdbcMigrationNode}.
     *
     * @return a fresh {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link JdbcMigrationNode}.
     *
     * <p>The identifier, name, environment, and UP SQL are required; the description, dependencies,
     * DOWN SQL, and autocommit flag are optional. UP/DOWN SQL may be supplied as a literal string,
     * read from a file, or loaded from a classpath resource.
     */
    public static class Builder {

        /** Creates a new {@code Builder}. */
        public Builder() {}

        private @Nullable NodeId id;
        private @Nullable String name;
        private @Nullable String description;
        private @Nullable JdbcEnvironment environment;
        private Set<NodeId> dependencies = Set.of();
        private @Nullable String upSql;
        private @Nullable String downSql;
        private boolean autocommit = false;

        /**
         * Sets the node identifier from a string value.
         *
         * @param id the identifier string, converted via {@link NodeId#of(String)}
         * @return this builder
         */
        public Builder id(String id) {
            this.id = NodeId.of(id);
            return this;
        }

        /**
         * Sets the node identifier.
         *
         * @param id the node identifier
         * @return this builder
         */
        public Builder id(NodeId id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the human readable node name.
         *
         * @param name the node name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the optional node description.
         *
         * @param description the description, or {@code null} for none
         * @return this builder
         */
        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the owning JDBC environment.
         *
         * @param environment the environment the node runs against
         * @return this builder
         */
        public Builder environment(JdbcEnvironment environment) {
            this.environment = environment;
            return this;
        }

        /**
         * Sets the dependency set, replacing any previously configured dependencies.
         *
         * @param dependencies the node identifiers this node depends on
         * @return this builder
         */
        public Builder dependencies(Set<NodeId> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        /**
         * Sets the dependencies from a varargs array, replacing any previously configured
         * dependencies.
         *
         * @param dependencies the node identifiers this node depends on
         * @return this builder
         */
        public Builder dependencies(NodeId... dependencies) {
            this.dependencies = Set.of(dependencies);
            return this;
        }

        /**
         * Sets the forward (UP) migration SQL from a literal string.
         *
         * @param sql the UP SQL
         * @return this builder
         */
        public Builder upSql(String sql) {
            this.upSql = sql;
            return this;
        }

        /**
         * Sets the forward (UP) migration SQL by reading it from a file.
         *
         * @param path the file to read the UP SQL from
         * @return this builder
         * @throws IOException if the file cannot be read
         */
        public Builder upSqlFromFile(Path path) throws IOException {
            this.upSql = Files.readString(path);
            return this;
        }

        /**
         * Sets the forward (UP) migration SQL by loading it from a classpath resource.
         *
         * @param resourcePath the classpath resource path
         * @return this builder
         * @throws IOException if the resource cannot be found or read
         */
        public Builder upSqlFromResource(String resourcePath) throws IOException {
            this.upSql = loadResource(resourcePath);
            return this;
        }

        /**
         * Sets the rollback (DOWN) migration SQL from a literal string.
         *
         * @param sql the DOWN SQL, or {@code null} to make the node non-reversible
         * @return this builder
         */
        public Builder downSql(@Nullable String sql) {
            this.downSql = sql;
            return this;
        }

        /**
         * Sets the rollback (DOWN) migration SQL by reading it from a file.
         *
         * @param path the file to read the DOWN SQL from
         * @return this builder
         * @throws IOException if the file cannot be read
         */
        public Builder downSqlFromFile(Path path) throws IOException {
            this.downSql = Files.readString(path);
            return this;
        }

        /**
         * Sets the rollback (DOWN) migration SQL by loading it from a classpath resource.
         *
         * @param resourcePath the classpath resource path
         * @return this builder
         * @throws IOException if the resource cannot be found or read
         */
        public Builder downSqlFromResource(String resourcePath) throws IOException {
            this.downSql = loadResource(resourcePath);
            return this;
        }

        /**
         * Sets whether the node's tasks run in autocommit mode.
         *
         * @param autocommit {@code true} to run without an enclosing transaction
         * @return this builder
         */
        public Builder autocommit(boolean autocommit) {
            this.autocommit = autocommit;
            return this;
        }

        /**
         * Builds the immutable {@link JdbcMigrationNode}.
         *
         * @return a new {@link JdbcMigrationNode}
         * @throws NullPointerException if a required attribute (id, name, environment, or UP SQL)
         *     was not set
         * @throws IllegalArgumentException if the UP SQL is blank
         */
        public JdbcMigrationNode build() {
            return new JdbcMigrationNode(this);
        }

        private String loadResource(String resourcePath) throws IOException {
            try (var is = getClass().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    throw new IOException("Resource not found: " + resourcePath);
                }
                return new String(is.readAllBytes(), UTF_8);
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JdbcMigrationNode other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "JdbcMigrationNode{"
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
