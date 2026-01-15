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
 * name: create_database
 * target: admin
 * autocommit: true  # トランザクション外で実行
 * up: "CREATE DATABASE myapp;"
 * down: "DROP DATABASE myapp;"
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

    /**
     * autocommit モードで実行するかどうか。
     *
     * <p>true の場合、トランザクションを使用せずに実行する。 CREATE DATABASE などトランザクション内で実行できない SQL 用。
     *
     * @return autocommit を有効にする場合は true を含む Optional、指定なしの場合は空
     */
    Optional<Boolean> autocommit();
}
