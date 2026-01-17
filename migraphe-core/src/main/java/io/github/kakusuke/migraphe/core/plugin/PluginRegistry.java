package io.github.kakusuke.migraphe.core.plugin;

import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * プラグインレジストリ - ServiceLoader でプラグインを読み込み、管理する。
 *
 * <p>読み込み優先順位:
 *
 * <ol>
 *   <li>クラスパス（testImplementation など）
 *   <li>個別 JAR ファイル（{@code loadFromJar()}）
 *   <li>plugins/ ディレクトリ（{@code loadFromDirectory()}）
 * </ol>
 *
 * <p>同じ type のプラグインが複数見つかった場合、後から読み込まれたものが優先される。
 */
public final class PluginRegistry {

    private final Map<String, MigraphePlugin<?>> plugins = new ConcurrentHashMap<>();

    /** クラスパスから ServiceLoader を使用してプラグインを読み込む。 */
    @SuppressWarnings("rawtypes")
    public void loadFromClasspath() {
        ServiceLoader<MigraphePlugin> loader = ServiceLoader.load(MigraphePlugin.class);
        for (MigraphePlugin plugin : loader) {
            register(plugin);
        }
    }

    /**
     * 指定された JAR ファイルからプラグインを読み込む。
     *
     * @param jarPath JAR ファイルのパス
     * @throws PluginLoadException 読み込みに失敗した場合
     */
    @SuppressWarnings("rawtypes")
    public void loadFromJar(Path jarPath) {
        if (!Files.exists(jarPath)) {
            throw new PluginLoadException("JAR file not found: " + jarPath);
        }

        if (!jarPath.toString().endsWith(".jar")) {
            throw new PluginLoadException("Not a JAR file: " + jarPath);
        }

        try {
            URL jarUrl = jarPath.toUri().toURL();
            URLClassLoader classLoader =
                    new URLClassLoader(new URL[] {jarUrl}, MigraphePlugin.class.getClassLoader());
            ServiceLoader<MigraphePlugin> loader =
                    ServiceLoader.load(MigraphePlugin.class, classLoader);

            int loadedCount = 0;
            for (MigraphePlugin plugin : loader) {
                register(plugin);
                loadedCount++;
            }

            if (loadedCount == 0) {
                throw new PluginLoadException("No plugins found in JAR: " + jarPath);
            }
        } catch (Exception e) {
            throw new PluginLoadException("Failed to load plugin from JAR: " + jarPath, e);
        }
    }

    /**
     * 指定されたディレクトリ内の全 JAR ファイルからプラグインを読み込む。
     *
     * @param pluginsDir プラグインディレクトリのパス
     * @throws PluginLoadException 読み込みに失敗した場合
     */
    public void loadFromDirectory(Path pluginsDir) {
        if (!Files.exists(pluginsDir)) {
            return; // ディレクトリが存在しない場合は何もしない
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
                    // 個別の JAR 読み込みエラーはログに記録して続行
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
     * プラグインを登録する。
     *
     * <p>同じ type のプラグインが既に存在する場合、上書きする（後勝ち）。
     *
     * @param plugin 登録するプラグイン
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
     * 指定された型のプラグインを取得する。
     *
     * @param type プラグインの型識別子
     * @return プラグイン（存在しない場合は null）
     */
    public @Nullable MigraphePlugin<?> getPlugin(String type) {
        return plugins.get(type);
    }

    /**
     * 指定された型のプラグインを取得する（必須）。
     *
     * <p>プラグインが見つからない場合、利用可能なプラグイン一覧と解決方法を含む詳細なエラーメッセージを持つ {@link PluginNotFoundException} をスローする。
     *
     * @param type プラグインの型識別子
     * @return プラグイン
     * @throws PluginNotFoundException 指定された型のプラグインが見つからない場合
     */
    public MigraphePlugin<?> getRequiredPlugin(String type) {
        MigraphePlugin<?> plugin = plugins.get(type);
        if (plugin == null) {
            throw new PluginNotFoundException(type, supportedTypes());
        }
        return plugin;
    }

    /**
     * サポートされているプラグインの型一覧を取得する。
     *
     * @return プラグインの型識別子のセット
     */
    public Set<String> supportedTypes() {
        return Set.copyOf(plugins.keySet());
    }

    /**
     * 登録されているプラグインの数を取得する。
     *
     * @return プラグイン数
     */
    public int size() {
        return plugins.size();
    }

    /** 全てのプラグインをクリアする（テスト用）。 */
    void clear() {
        plugins.clear();
    }
}
