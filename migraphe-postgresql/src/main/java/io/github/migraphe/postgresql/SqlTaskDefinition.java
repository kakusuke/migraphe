package io.github.migraphe.postgresql;

import io.github.migraphe.api.spi.TaskDefinition;
import io.smallrye.config.ConfigMapping;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL 用の TaskDefinition サブタイプ。
 *
 * <p>YAML ファイルから直接マッピングされる。up/down は SQL 文字列。
 *
 * <p>YAML 例:
 *
 * <pre>{@code
 * name: create_users
 * target: db1
 * dependencies:
 *   - initial_setup
 * up: "CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100));"
 * down: "DROP TABLE users;"
 * }</pre>
 */
@ConfigMapping(prefix = "")
public interface SqlTaskDefinition extends TaskDefinition<String> {

    @Override
    String name();

    @Override
    Optional<String> description();

    @Override
    String target();

    @Override
    Optional<List<String>> dependencies();

    @Override
    String up();

    @Override
    Optional<String> down();
}
