package io.github.kakusuke.migraphe.core.generator;

import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GeneratorPlugin レジストリ — ServiceLoader で GeneratorSourcePlugin / GeneratorOutputPlugin を発見・管理する。
 *
 * <p>同じ type のプラグインが複数見つかった場合、後から登録されたものが優先される。
 */
public final class GeneratorRegistry {

    private final Map<String, GeneratorSourcePlugin<?>> sourcePlugins = new ConcurrentHashMap<>();
    private final Map<String, GeneratorOutputPlugin> outputPlugins = new ConcurrentHashMap<>();

    /** クラスパスから ServiceLoader を使用してプラグインを読み込む。 */
    public void loadFromClasspath() {
        for (GeneratorSourcePlugin<?> plugin : ServiceLoader.load(GeneratorSourcePlugin.class)) {
            registerSource(plugin);
        }
        for (GeneratorOutputPlugin plugin : ServiceLoader.load(GeneratorOutputPlugin.class)) {
            registerOutput(plugin);
        }
    }

    /**
     * 指定された ClassLoader を使用してプラグインを読み込む。
     *
     * @param classLoader プラグインを探索する ClassLoader
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
     * 指定されたディレクトリ内の JAR ファイルからプラグインを読み込む。
     *
     * @param pluginsDir プラグイン JAR を含むディレクトリ
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

    void registerSource(GeneratorSourcePlugin<?> plugin) {
        Objects.requireNonNull(plugin, "plugin must not be null");
        String type = plugin.type();
        Objects.requireNonNull(type, "plugin.type() must not be null");
        sourcePlugins.put(type, plugin);
    }

    public Optional<GeneratorSourcePlugin<?>> findSourceByType(String type) {
        return Optional.ofNullable(sourcePlugins.get(type));
    }

    void registerOutput(GeneratorOutputPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin must not be null");
        String type = plugin.type();
        Objects.requireNonNull(type, "plugin.type() must not be null");
        outputPlugins.put(type, plugin);
    }

    public Optional<GeneratorOutputPlugin> findOutputByType(String type) {
        return Optional.ofNullable(outputPlugins.get(type));
    }
}
