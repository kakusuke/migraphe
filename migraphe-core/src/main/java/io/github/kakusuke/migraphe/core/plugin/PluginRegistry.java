package io.github.kakusuke.migraphe.core.plugin;

import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Registry that discovers, loads, and looks up {@link MigraphePlugin} implementations.
 *
 * <p>Plugins are discovered through the {@link ServiceLoader} mechanism and indexed by their {@link
 * MigraphePlugin#type() type} identifier. The runtime resolves a configuration {@code type} value
 * to a plugin instance through this registry. Plugins can be loaded from several sources, typically
 * in this order:
 *
 * <ol>
 *   <li>the classpath (for example {@code testImplementation} dependencies)
 *   <li>individual JAR files ({@link #loadFromJar(Path)})
 *   <li>a {@code plugins/} directory ({@link #loadFromDirectory(Path)})
 * </ol>
 *
 * <p>When more than one plugin declares the same {@code type}, the most recently loaded one wins
 * (last-write-wins).
 *
 * <p>Any {@link URLClassLoader} created internally by {@link #loadFromJar(Path)} or {@link
 * #loadFromDirectory(Path)} is owned by this registry and released by {@link #close()}. In
 * long-lived processes (such as the Gradle daemon), wrap the registry in a try-with-resources block
 * to avoid leaking class loaders.
 *
 * <p>This class is thread-safe: plugins are stored in a {@link ConcurrentHashMap} and owned class
 * loaders in a {@link CopyOnWriteArrayList}.
 */
public final class PluginRegistry implements AutoCloseable {

    /** Creates a new {@code PluginRegistry}. */
    public PluginRegistry() {}

    private final Map<String, MigraphePlugin<?>> plugins = new ConcurrentHashMap<>();
    private final List<URLClassLoader> ownedClassLoaders = new CopyOnWriteArrayList<>();

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
     * Loads plugins from a single JAR file.
     *
     * <p>A dedicated {@link URLClassLoader} is created for the JAR (parented to the class loader of
     * {@link MigraphePlugin}), the JAR is scanned via {@link ServiceLoader}, and every discovered
     * plugin is registered. The created class loader becomes owned by this registry and is closed
     * by {@link #close()}. If no plugins are found, the class loader is closed immediately and an
     * exception is thrown.
     *
     * @param jarPath the path to the JAR file to load
     * @throws PluginLoadException if the path does not exist, is not a {@code .jar} file, contains
     *     no plugins, or cannot be read
     */
    @SuppressWarnings("rawtypes")
    public void loadFromJar(Path jarPath) {
        if (!Files.exists(jarPath)) {
            throw new PluginLoadException("JAR file not found: " + jarPath);
        }

        if (!jarPath.toString().endsWith(".jar")) {
            throw new PluginLoadException("Not a JAR file: " + jarPath);
        }

        URLClassLoader classLoader = null;
        try {
            URL jarUrl = jarPath.toUri().toURL();
            classLoader =
                    new URLClassLoader(new URL[] {jarUrl}, MigraphePlugin.class.getClassLoader());
            ServiceLoader<MigraphePlugin> loader =
                    ServiceLoader.load(MigraphePlugin.class, classLoader);

            int loadedCount = 0;
            for (MigraphePlugin plugin : loader) {
                register(plugin);
                loadedCount++;
            }

            if (loadedCount == 0) {
                try {
                    classLoader.close();
                } catch (IOException ignored) {
                    // I/O errors while closing the class loader are swallowed.
                }
                throw new PluginLoadException("No plugins found in JAR: " + jarPath);
            }
            ownedClassLoaders.add(classLoader);
        } catch (PluginLoadException e) {
            throw e;
        } catch (Exception e) {
            if (classLoader != null) {
                try {
                    classLoader.close();
                } catch (IOException ignored) {
                    // I/O errors while closing the class loader are swallowed.
                }
            }
            throw new PluginLoadException("Failed to load plugin from JAR: " + jarPath, e);
        }
    }

    /**
     * Closes every {@link URLClassLoader} this registry created via {@link #loadFromJar(Path)} or
     * {@link #loadFromDirectory(Path)}.
     *
     * <p>Class loaders supplied externally (for example to {@link
     * #loadFromClassLoader(ClassLoader)}) are not owned by this registry and are therefore not
     * closed.
     */
    @Override
    public void close() {
        for (URLClassLoader cl : ownedClassLoaders) {
            try {
                cl.close();
            } catch (IOException ignored) {
                // I/O errors while closing are swallowed; the loaded classes are already retained
                // by
                // the JVM.
            }
        }
        ownedClassLoaders.clear();
    }

    /**
     * Loads plugins from every JAR file directly inside the given directory.
     *
     * <p>If the directory does not exist, this method does nothing. Each JAR is loaded via {@link
     * #loadFromJar(Path)}; a failure to load an individual JAR is logged to {@code System.err} and
     * does not abort the scan of the remaining JARs.
     *
     * @param pluginsDir the directory to scan for plugin JAR files
     * @throws PluginLoadException if {@code pluginsDir} exists but is not a directory, or if the
     *     directory cannot be listed
     */
    public void loadFromDirectory(Path pluginsDir) {
        if (!Files.exists(pluginsDir)) {
            return; // Nothing to do when the directory does not exist.
        }

        if (!Files.isDirectory(pluginsDir)) {
            throw new PluginLoadException("Not a directory: " + pluginsDir);
        }

        try (Stream<Path> files = Files.list(pluginsDir)) {
            List<Path> jarFiles =
                    files.filter(path -> path.toString().endsWith(".jar"))
                            .collect(Collectors.toList());

            for (Path jarFile : jarFiles) {
                try {
                    loadFromJar(jarFile);
                } catch (PluginLoadException e) {
                    // Log and continue: an error loading one JAR must not abort the others.
                    System.err.println("Warning: " + e.getMessage());
                    if (e.getCause() != null) {
                        System.err.println("  Caused by: " + e.getCause());
                    }
                }
            }
        } catch (Exception e) {
            throw new PluginLoadException("Failed to scan plugins directory: " + pluginsDir, e);
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
