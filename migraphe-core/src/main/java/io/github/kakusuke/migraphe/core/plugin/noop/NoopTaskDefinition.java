package io.github.kakusuke.migraphe.core.plugin.noop;

import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.smallrye.config.ConfigMapping;
import java.util.List;
import java.util.Optional;

/**
 * noop プラグイン用の TaskDefinition。
 *
 * <p>up/down は説明テキスト（SQL ではない）。noop プラグインは読むだけで実行しない。
 *
 * <p>YAML 例:
 *
 * <pre>{@code
 * name: Create users table
 * target: main
 * up: "Create the users table"
 * down: "Drop the users table"
 * }</pre>
 */
@ConfigMapping(prefix = "")
public interface NoopTaskDefinition extends TaskDefinition<String> {

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
