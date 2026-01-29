package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;

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

    /** PluginRegistry を作成する。 */
    protected PluginRegistry createPluginRegistry() {
        PluginRegistry registry = new PluginRegistry();

        // クラスパスからロード（テスト時など）
        registry.loadFromClasspath();

        // migraphePlugin configuration の JAR からロード
        List<URL> urls = new ArrayList<>();
        for (File file : getPluginClasspath().getFiles()) {
            try {
                urls.add(file.toURI().toURL());
            } catch (MalformedURLException e) {
                getLogger().warn("Failed to convert file to URL: " + file, e);
            }
        }

        if (!urls.isEmpty()) {
            URLClassLoader classLoader =
                    new URLClassLoader(
                            urls.toArray(new URL[0]),
                            io.github.kakusuke.migraphe.api.spi.MigraphePlugin.class
                                    .getClassLoader());
            registry.loadFromClassLoader(classLoader);
        }

        return registry;
    }

    /** ExecutionContext をロードする。 */
    protected ExecutionContext loadExecutionContext() {
        PluginRegistry registry = createPluginRegistry();
        return ExecutionContext.load(
                getBaseDir().get().getAsFile().toPath(), registry, getVariables().get());
    }
}
