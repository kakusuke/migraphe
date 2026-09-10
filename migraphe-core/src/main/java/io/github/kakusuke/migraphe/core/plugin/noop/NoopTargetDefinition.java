package io.github.kakusuke.migraphe.core.plugin.noop;

import io.github.kakusuke.migraphe.api.spi.TargetDefinition;
import io.smallrye.config.ConfigMapping;

/**
 * {@link TargetDefinition} for the {@code "noop"} plugin.
 *
 * <p>The noop plugin needs no connection settings, so this definition adds nothing beyond the
 * {@link #type()} discriminator inherited from {@link TargetDefinition}. It is a SmallRye
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
public interface NoopTargetDefinition extends TargetDefinition {

    /**
     * Returns the target type identifier, which is always {@code "noop"} for this plugin.
     *
     * @return the target type identifier
     */
    @Override
    String type();
}
