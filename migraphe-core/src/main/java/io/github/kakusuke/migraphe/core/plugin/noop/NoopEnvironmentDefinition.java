package io.github.kakusuke.migraphe.core.plugin.noop;

import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.smallrye.config.ConfigMapping;

/**
 * noop プラグイン用の EnvironmentDefinition。
 *
 * <p>YAML 例 (targets/main.yaml):
 *
 * <pre>{@code
 * type: noop
 * }</pre>
 */
@ConfigMapping(prefix = "")
public interface NoopEnvironmentDefinition extends EnvironmentDefinition {

    @Override
    String type();
}
