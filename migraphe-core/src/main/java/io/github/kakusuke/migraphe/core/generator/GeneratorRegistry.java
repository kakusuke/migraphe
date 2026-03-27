package io.github.kakusuke.migraphe.core.generator;

import io.github.kakusuke.migraphe.generator.api.GeneratorPlugin;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GeneratorPlugin レジストリ — ServiceLoader で GeneratorPlugin を発見・管理する。
 *
 * <p>同じ type のプラグインが複数見つかった場合、後から登録されたものが優先される。
 */
public final class GeneratorRegistry {

    private final Map<String, GeneratorPlugin> plugins = new ConcurrentHashMap<>();

    /** クラスパスから ServiceLoader を使用してプラグインを読み込む。 */
    public void loadFromClasspath() {
        ServiceLoader<GeneratorPlugin> loader = ServiceLoader.load(GeneratorPlugin.class);
        for (GeneratorPlugin plugin : loader) {
            register(plugin);
        }
    }

    /**
     * 指定された ClassLoader を使用してプラグインを読み込む。
     *
     * @param classLoader プラグインを探索する ClassLoader
     */
    public void loadFromClassLoader(ClassLoader classLoader) {
        ServiceLoader<GeneratorPlugin> loader =
                ServiceLoader.load(GeneratorPlugin.class, classLoader);
        for (GeneratorPlugin plugin : loader) {
            register(plugin);
        }
    }

    /**
     * プラグインを登録する。
     *
     * @param plugin 登録するプラグイン
     */
    void register(GeneratorPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin must not be null");
        String type = plugin.type();
        Objects.requireNonNull(type, "plugin.type() must not be null");
        plugins.put(type, plugin);
    }

    /**
     * 指定された型のプラグインを取得する。
     *
     * @param type プラグインの型識別子
     * @return プラグイン（存在しない場合は empty）
     */
    public Optional<GeneratorPlugin> findByType(String type) {
        return Optional.ofNullable(plugins.get(type));
    }
}
