package io.github.kakusuke.migraphe.core.plugin.noop;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.EnvironmentProvider;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;

/**
 * {@link EnvironmentProvider} for the {@code "noop"} plugin.
 *
 * <p>Produces a stateless {@link SimpleEnvironment} from the target name. The supplied {@link
 * EnvironmentDefinition} carries no connection details for the noop plugin and is therefore
 * ignored.
 *
 * @see NoopPlugin
 */
public final class NoopEnvironmentProvider implements EnvironmentProvider {

    /** Creates a new {@code NoopEnvironmentProvider}. */
    public NoopEnvironmentProvider() {}

    /**
     * Creates a {@link SimpleEnvironment} named after the target.
     *
     * @param name the environment name (the target ID from configuration)
     * @param definition the noop environment definition; ignored, as no connection state is needed
     * @return a {@link SimpleEnvironment} whose ID and name are both {@code name}
     */
    @Override
    public Environment createEnvironment(String name, EnvironmentDefinition definition) {
        return SimpleEnvironment.create(name);
    }
}
