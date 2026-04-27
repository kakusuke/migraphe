package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.jspecify.annotations.Nullable;

/** migraphe タスクの共通基底クラス。 */
public abstract class AbstractMigrapheTask extends DefaultTask {

    /** プロジェクト設定のベースディレクトリ。 */
    @InputDirectory
    public abstract DirectoryProperty getBaseDir();

    /** SmallRye Config に差し込む変数マップ。 */
    @Input
    public abstract MapProperty<String, String> getVariables();

    /** migraphePlugin configuration の解決済み JAR パス。 */
    @InputFiles
    public abstract ConfigurableFileCollection getPluginClasspath();

    /** migraphePlugin configuration の JAR から URLClassLoader を作成する。 */
    protected @Nullable URLClassLoader createPluginClassLoader() {
        List<URL> urls = new ArrayList<>();
        for (File file : getPluginClasspath().getFiles()) {
            try {
                urls.add(file.toURI().toURL());
            } catch (MalformedURLException e) {
                getLogger().warn("Failed to convert file to URL: " + file, e);
            }
        }

        if (urls.isEmpty()) {
            return null;
        }

        return new URLClassLoader(urls.toArray(new URL[0]), MigraphePlugin.class.getClassLoader());
    }

    /** PluginRegistry を作成する。 */
    protected PluginRegistry createPluginRegistry(@Nullable URLClassLoader pluginClassLoader) {
        PluginRegistry registry = new PluginRegistry();

        // クラスパスからロード（テスト時など）
        registry.loadFromClasspath();

        // migraphePlugin configuration の JAR からロード
        if (pluginClassLoader != null) {
            registry.loadFromClassLoader(pluginClassLoader);
        }

        return registry;
    }

    /** ExecutionContext をロードする。 */
    protected ExecutionContext loadExecutionContext() {
        return loadExecutionContext(createPluginClassLoader());
    }

    /** ExecutionContext をロードする（外部から作成した classLoader を共有する場合）。 */
    protected ExecutionContext loadExecutionContext(@Nullable URLClassLoader pluginClassLoader) {
        PluginRegistry registry = createPluginRegistry(pluginClassLoader);
        return ExecutionContext.load(
                getBaseDir().get().getAsFile().toPath(), registry, getVariables().get());
    }

    /**
     * ExecutionContext をロードし action を実行する。pluginClassLoader は finally で閉じる。 Gradle daemon
     * でのリソースリークを防ぐためのヘルパー。
     */
    protected void withExecutionContext(Consumer<ExecutionContext> action) {
        URLClassLoader pluginClassLoader = createPluginClassLoader();
        try {
            ExecutionContext context = loadExecutionContext(pluginClassLoader);
            action.accept(context);
        } finally {
            closePluginClassLoader(pluginClassLoader);
        }
    }

    /** pluginClassLoader を安全に閉じる。null は無視。 */
    protected void closePluginClassLoader(@Nullable URLClassLoader pluginClassLoader) {
        if (pluginClassLoader == null) {
            return;
        }
        try {
            pluginClassLoader.close();
        } catch (IOException e) {
            getLogger().warn("Failed to close plugin classloader", e);
        }
    }
}
