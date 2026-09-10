package io.github.kakusuke.migraphe.core.plugin;

import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Registry that discovers, loads, and looks up {@link MigraphePlugin} implementations.
 *
 * <p>Plugins are discovered through the {@link ServiceLoader} mechanism and indexed by their {@link
 * MigraphePlugin#type() type} identifier. The runtime resolves a configuration {@code type} value
 * to a plugin instance through this registry. Plugins come from the classpath ({@link
 * #loadFromClasspath()}) and from a class loader the caller supplies ({@link
 * #loadFromClassLoader(ClassLoader)}) — the Maven-resolved one in the CLI, the {@code
 * migraphePlugin} configuration's in Gradle.
 *
 * <p>This registry creates no class loader of its own, so it owns none and has nothing to close.
 * The loader passed to {@link #loadFromClassLoader(ClassLoader)} stays the caller's to close, which
 * matters in a long-lived process such as the Gradle daemon.
 *
 * <p>When more than one plugin declares the same {@code type}, the most recently loaded one wins
 * (last-write-wins).
 *
 * <p>This class is thread-safe: plugins are stored in a {@link ConcurrentHashMap}.
 */
public final class PluginRegistry {

    /** Creates a new {@code PluginRegistry}. */
    public PluginRegistry() {}

    private final Map<String, MigraphePlugin<?>> plugins = new ConcurrentHashMap<>();

    /**
     * Loads all plugins discoverable on the current thread's context classpath via {@link
     * ServiceLoader}.
     *
     * <p>Each discovered plugin is registered under its {@link MigraphePlugin#type() type},
     * replacing any previously registered plugin with the same type.
     */
    @SuppressWarnings("rawtypes")
    public void loadFromClasspath() {
        ServiceLoader<MigraphePlugin> loader = ServiceLoader.load(MigraphePlugin.class);
        for (MigraphePlugin plugin : loader) {
            register(plugin);
        }
    }

    /**
     * Loads all plugins discoverable via {@link ServiceLoader} from the given class loader.
     *
     * <p>Use this when plugins must be discovered from a custom class loader, such as the isolated
     * class loader used by the Gradle plugin.
     *
     * @param classLoader the class loader to scan for plugins
     */
    @SuppressWarnings("rawtypes")
    public void loadFromClassLoader(ClassLoader classLoader) {
        ServiceLoader<MigraphePlugin> loader =
                ServiceLoader.load(MigraphePlugin.class, classLoader);
        for (MigraphePlugin plugin : loader) {
            register(plugin);
        }
    }

    /**
     * Registers a plugin under its {@link MigraphePlugin#type() type}.
     *
     * <p>If a plugin with the same type is already registered, it is overwritten (last-write-wins).
     *
     * @param plugin the plugin to register
     * @throws NullPointerException if {@code plugin} or its {@link MigraphePlugin#type() type} is
     *     {@code null}
     * @throws PluginLoadException if the plugin's type is blank
     */
    void register(MigraphePlugin<?> plugin) {
        Objects.requireNonNull(plugin, "plugin must not be null");
        String type = plugin.type();
        Objects.requireNonNull(type, "plugin.type() must not be null");

        if (type.isBlank()) {
            throw new PluginLoadException("Plugin type must not be blank");
        }

        plugins.put(type, plugin);
    }

    /**
     * Returns whether a plugin is registered for the given type.
     *
     * @param type the plugin type identifier to check
     * @return {@code true} if a plugin is registered under {@code type}, {@code false} otherwise
     */
    public boolean hasPlugin(String type) {
        return plugins.containsKey(type);
    }

    /**
     * Returns the plugin registered for the given type, or {@code null} if none is registered.
     *
     * @param type the plugin type identifier to look up
     * @return the registered plugin, or {@code null} if no plugin is registered under {@code type}
     */
    public @Nullable MigraphePlugin<?> getPlugin(String type) {
        return plugins.get(type);
    }

    /**
     * Returns the plugin registered for the given type, failing if none is registered.
     *
     * <p>When no plugin is found, the thrown {@link PluginNotFoundException} carries a detailed
     * message that lists the {@linkplain #supportedTypes() available types} and explains how to
     * make the requested plugin available.
     *
     * @param type the plugin type identifier to look up
     * @return the registered plugin
     * @throws PluginNotFoundException if no plugin is registered under {@code type}
     */
    public MigraphePlugin<?> getRequiredPlugin(String type) {
        MigraphePlugin<?> plugin = plugins.get(type);
        if (plugin == null) {
            throw new PluginNotFoundException(type, supportedTypes());
        }
        return plugin;
    }

    /**
     * Returns the set of plugin types currently registered.
     *
     * @return an immutable copy of the registered plugin type identifiers
     */
    public Set<String> supportedTypes() {
        return Set.copyOf(plugins.keySet());
    }

    /**
     * Returns the number of registered plugins.
     *
     * @return the count of registered plugins
     */
    public int size() {
        return plugins.size();
    }

    /** Removes all registered plugins. Intended for tests. */
    void clear() {
        plugins.clear();
    }
}
