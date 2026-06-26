package io.github.kakusuke.migraphe.core.generator;

import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry that discovers and manages generator plugins.
 *
 * <p>It loads both halves of the generator system — {@link GeneratorSourcePlugin} (data extraction)
 * and {@link GeneratorOutputPlugin} (rendering) — through the {@link ServiceLoader} mechanism, and
 * indexes each by its {@code type()} identifier so that {@link GeneratorExecutor} can look them up
 * by the {@code source.type}/{@code type} values from configuration. Plugins can be loaded from the
 * application classpath, from an arbitrary {@link ClassLoader}, or from a directory of plugin JARs.
 *
 * <p>When two plugins report the same {@code type()}, the most recently registered one wins (it
 * overwrites the earlier entry).
 *
 * <p>{@link URLClassLoader URLClassLoaders} created internally by {@link #loadFromDirectory(Path)}
 * are owned by this registry and released by {@link #close()}; externally supplied class loaders
 * are never closed. In long-lived processes such as the Gradle daemon, wrap the registry in a
 * try-with-resources to avoid leaking those class loaders.
 */
public final class GeneratorRegistry implements AutoCloseable {

    /** Creates a new {@code GeneratorRegistry}. */
    public GeneratorRegistry() {}

    private final Map<String, GeneratorSourcePlugin<?>> sourcePlugins = new ConcurrentHashMap<>();
    private final Map<String, GeneratorOutputPlugin> outputPlugins = new ConcurrentHashMap<>();
    private final List<URLClassLoader> ownedClassLoaders = new CopyOnWriteArrayList<>();

    /**
     * Loads plugins from the current thread/application classpath via {@link ServiceLoader}.
     *
     * <p>Both source and output plugins declared in {@code META-INF/services} resources on the
     * classpath are discovered and registered.
     */
    public void loadFromClasspath() {
        for (GeneratorSourcePlugin<?> plugin : ServiceLoader.load(GeneratorSourcePlugin.class)) {
            registerSource(plugin);
        }
        for (GeneratorOutputPlugin plugin : ServiceLoader.load(GeneratorOutputPlugin.class)) {
            registerOutput(plugin);
        }
    }

    /**
     * Loads plugins discoverable through the given class loader via {@link ServiceLoader}.
     *
     * @param classLoader the class loader to search for source and output plugin service entries
     */
    public void loadFromClassLoader(ClassLoader classLoader) {
        for (GeneratorSourcePlugin<?> plugin :
                ServiceLoader.load(GeneratorSourcePlugin.class, classLoader)) {
            registerSource(plugin);
        }
        for (GeneratorOutputPlugin plugin :
                ServiceLoader.load(GeneratorOutputPlugin.class, classLoader)) {
            registerOutput(plugin);
        }
    }

    /**
     * Loads plugins from every {@code .jar} file in the given directory.
     *
     * <p>For each JAR a child {@link URLClassLoader} (parented to this class's loader) is created,
     * retained as an owned resource, and searched via {@link ServiceLoader}. If the directory does
     * not exist or is not a directory, the call is a no-op. The owned class loaders are released by
     * {@link #close()}.
     *
     * @param pluginsDir the directory containing plugin JAR files
     * @throws IllegalStateException if the directory cannot be scanned, or if a particular JAR
     *     cannot be loaded
     */
    public void loadFromDirectory(Path pluginsDir) {
        if (!Files.isDirectory(pluginsDir)) {
            return;
        }
        try (var entries = Files.list(pluginsDir)) {
            entries.filter(p -> p.toString().endsWith(".jar"))
                    .forEach(
                            jarPath -> {
                                try {
                                    URL jarUrl = jarPath.toUri().toURL();
                                    URLClassLoader classLoader =
                                            new URLClassLoader(
                                                    new URL[] {jarUrl},
                                                    GeneratorRegistry.class.getClassLoader());
                                    ownedClassLoaders.add(classLoader);
                                    loadFromClassLoader(classLoader);
                                } catch (Exception e) {
                                    throw new IllegalStateException(
                                            "Failed to load generator plugin from: " + jarPath, e);
                                }
                            });
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to scan generator plugins directory: " + pluginsDir, e);
        }
    }

    /**
     * Closes every {@link URLClassLoader} this registry created in {@link
     * #loadFromDirectory(Path)}.
     *
     * <p>Class loaders supplied by callers ({@link #loadFromClassLoader(ClassLoader)}) are not
     * owned and are left open. {@link IOException}s raised while closing are swallowed, since the
     * loaded classes are already resident in the JVM.
     */
    @Override
    public void close() {
        for (URLClassLoader cl : ownedClassLoaders) {
            try {
                cl.close();
            } catch (IOException ignored) {
                // Ignore I/O errors on close: the classes are already held by the JVM.
            }
        }
        ownedClassLoaders.clear();
    }

    /**
     * Registers a source plugin under its {@link GeneratorSourcePlugin#type() type}, replacing any
     * existing plugin with the same type.
     *
     * @param plugin the source plugin to register; neither it nor its {@code type()} may be null
     * @throws NullPointerException if {@code plugin} or its reported type is {@code null}
     */
    void registerSource(GeneratorSourcePlugin<?> plugin) {
        Objects.requireNonNull(plugin, "plugin must not be null");
        String type = plugin.type();
        Objects.requireNonNull(type, "plugin.type() must not be null");
        sourcePlugins.put(type, plugin);
    }

    /**
     * Looks up a registered source plugin by its type identifier.
     *
     * @param type the source-type identifier to match against {@link GeneratorSourcePlugin#type()}
     * @return an {@link Optional} containing the matching source plugin, or {@link
     *     Optional#empty()} if none is registered for the type
     */
    public Optional<GeneratorSourcePlugin<?>> findSourceByType(String type) {
        return Optional.ofNullable(sourcePlugins.get(type));
    }

    /**
     * Registers an output plugin under its {@link GeneratorOutputPlugin#type() type}, replacing any
     * existing plugin with the same type.
     *
     * @param plugin the output plugin to register; neither it nor its {@code type()} may be null
     * @throws NullPointerException if {@code plugin} or its reported type is {@code null}
     */
    void registerOutput(GeneratorOutputPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin must not be null");
        String type = plugin.type();
        Objects.requireNonNull(type, "plugin.type() must not be null");
        outputPlugins.put(type, plugin);
    }

    /**
     * Looks up a registered output plugin by its type identifier.
     *
     * @param type the output-type identifier to match against {@link GeneratorOutputPlugin#type()}
     * @return an {@link Optional} containing the matching output plugin, or {@link
     *     Optional#empty()} if none is registered for the type
     */
    public Optional<GeneratorOutputPlugin> findOutputByType(String type) {
        return Optional.ofNullable(outputPlugins.get(type));
    }
}
