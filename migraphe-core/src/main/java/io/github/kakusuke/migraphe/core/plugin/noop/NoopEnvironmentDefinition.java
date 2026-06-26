package io.github.kakusuke.migraphe.core.plugin.noop;

import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.smallrye.config.ConfigMapping;

/**
 * {@link EnvironmentDefinition} for the {@code "noop"} plugin.
 *
 * <p>The noop plugin needs no connection settings, so this definition adds nothing beyond the
 * {@link #type()} discriminator inherited from {@link EnvironmentDefinition}. It is a SmallRye
 * {@code @ConfigMapping} interface, so its single property binds directly from a target's YAML.
 *
 * <p>Example target YAML ({@code targets/main.yaml}):
 *
 * <pre>{@code
 * type: noop
 * }</pre>
 *
 * @see NoopPlugin
 */
@ConfigMapping(prefix = "")
public interface NoopEnvironmentDefinition extends EnvironmentDefinition {

    /**
     * Returns the environment type identifier, which is always {@code "noop"} for this plugin.
     *
     * @return the environment type identifier
     */
    @Override
    String type();
}
