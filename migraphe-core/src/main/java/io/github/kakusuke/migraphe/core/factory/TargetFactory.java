package io.github.kakusuke.migraphe.core.factory;

import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.TargetDefinition;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.core.config.ConfigurationException;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic factory that builds {@link Target}s from {@link TargetDefinition}s via plugins.
 *
 * <p>For each definition, the factory resolves the plugin matching the definition's {@code type}
 * from the {@link PluginRegistry} and delegates creation to that plugin's {@link
 * io.github.kakusuke.migraphe.api.spi.TargetProvider}. Configuration errors surface as a {@link
 * ConfigurationException} from elsewhere in the loading pipeline.
 */
public class TargetFactory {

    private final PluginRegistry pluginRegistry;

    /**
     * Creates an target factory.
     *
     * @param pluginRegistry the registry used to resolve plugins by target type
     */
    public TargetFactory(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    /**
     * Builds a single {@link Target} from its definition.
     *
     * @param targetId the target ID this target belongs to (e.g. {@code "db1"})
     * @param definition the target definition, whose {@code type} selects the plugin
     * @return the created target
     * @throws io.github.kakusuke.migraphe.core.plugin.PluginNotFoundException if no plugin is
     *     registered for the definition's type
     */
    public Target createTarget(String targetId, TargetDefinition definition) {
        String type = definition.type();

        // Resolve the plugin.
        MigraphePlugin<?> plugin = pluginRegistry.getRequiredPlugin(type);

        // Create the target via the plugin's TargetProvider.
        return plugin.targetProvider().createTarget(targetId, definition);
    }

    /**
     * Builds a map of {@link Target}s from a map of definitions, preserving iteration order.
     *
     * @param definitions map of target ID to its {@link TargetDefinition}
     * @return map of target ID to the created {@link Target}
     */
    public Map<String, Target> createTargets(Map<String, TargetDefinition> definitions) {
        Map<String, Target> targets = new LinkedHashMap<>();

        for (Map.Entry<String, TargetDefinition> entry : definitions.entrySet()) {
            String targetId = entry.getKey();
            TargetDefinition definition = entry.getValue();
            Target target = createTarget(targetId, definition);
            targets.put(targetId, target);
        }

        return targets;
    }
}
