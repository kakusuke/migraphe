package io.github.kakusuke.migraphe.core.factory;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.core.config.ConfigurationException;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic factory that builds {@link Environment}s from {@link EnvironmentDefinition}s via plugins.
 *
 * <p>For each definition, the factory resolves the plugin matching the definition's {@code type}
 * from the {@link PluginRegistry} and delegates creation to that plugin's {@link
 * io.github.kakusuke.migraphe.api.spi.EnvironmentProvider}. Configuration errors surface as a
 * {@link ConfigurationException} from elsewhere in the loading pipeline.
 */
public class EnvironmentFactory {

    private final PluginRegistry pluginRegistry;

    /**
     * Creates an environment factory.
     *
     * @param pluginRegistry the registry used to resolve plugins by environment type
     */
    public EnvironmentFactory(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    /**
     * Builds a single {@link Environment} from its definition.
     *
     * @param targetId the target ID this environment belongs to (e.g. {@code "db1"})
     * @param definition the environment definition, whose {@code type} selects the plugin
     * @return the created environment
     * @throws io.github.kakusuke.migraphe.core.plugin.PluginNotFoundException if no plugin is
     *     registered for the definition's type
     */
    public Environment createEnvironment(String targetId, EnvironmentDefinition definition) {
        String type = definition.type();

        // Resolve the plugin.
        MigraphePlugin<?> plugin = pluginRegistry.getRequiredPlugin(type);

        // Create the environment via the plugin's EnvironmentProvider.
        return plugin.environmentProvider().createEnvironment(targetId, definition);
    }

    /**
     * Builds a map of {@link Environment}s from a map of definitions, preserving iteration order.
     *
     * @param definitions map of target ID to its {@link EnvironmentDefinition}
     * @return map of target ID to the created {@link Environment}
     */
    public Map<String, Environment> createEnvironments(
            Map<String, EnvironmentDefinition> definitions) {
        Map<String, Environment> environments = new LinkedHashMap<>();

        for (Map.Entry<String, EnvironmentDefinition> entry : definitions.entrySet()) {
            String targetId = entry.getKey();
            EnvironmentDefinition definition = entry.getValue();
            Environment env = createEnvironment(targetId, definition);
            environments.put(targetId, env);
        }

        return environments;
    }
}
