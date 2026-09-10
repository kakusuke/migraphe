package io.github.kakusuke.migraphe.core.plugin.noop;

import io.github.kakusuke.migraphe.api.spi.TargetDefinition;
import io.github.kakusuke.migraphe.api.spi.TargetProvider;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.core.plugin.SimpleTarget;

/**
 * {@link TargetProvider} for the {@code "noop"} plugin.
 *
 * <p>Produces a stateless {@link SimpleTarget} from the target name. The supplied {@link
 * TargetDefinition} carries no connection details for the noop plugin and is therefore ignored.
 *
 * @see NoopPlugin
 */
public final class NoopTargetProvider implements TargetProvider {

    /** Creates a new {@code NoopTargetProvider}. */
    public NoopTargetProvider() {}

    /**
     * Creates a {@link SimpleTarget} named after the target.
     *
     * @param name the environment name (the target ID from configuration)
     * @param definition the noop target definition; ignored, as no connection state is needed
     * @return a {@link SimpleTarget} whose ID and name are both {@code name}
     */
    @Override
    public Target createTarget(String name, TargetDefinition definition) {
        return SimpleTarget.create(name);
    }
}
