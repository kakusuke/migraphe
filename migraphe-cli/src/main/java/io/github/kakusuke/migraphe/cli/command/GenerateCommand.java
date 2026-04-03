package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.cli.util.AnsiColor;
import io.github.kakusuke.migraphe.core.config.ProjectConfig;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.generator.GeneratorExecutor;
import io.github.kakusuke.migraphe.core.generator.GeneratorRegistry;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** ジェネレーターを実行するコマンド。 */
public class GenerateCommand implements Command {

    private final Path baseDir;
    private final PluginRegistry pluginRegistry;
    private final @Nullable URLClassLoader pluginClassLoader;
    private final @Nullable String nameFilter;
    private final boolean colorEnabled;

    public GenerateCommand(
            Path baseDir,
            PluginRegistry pluginRegistry,
            @Nullable URLClassLoader pluginClassLoader,
            @Nullable String nameFilter) {
        this(baseDir, pluginRegistry, pluginClassLoader, nameFilter, AnsiColor.isColorEnabled());
    }

    /** テスト用コンストラクタ。 */
    public GenerateCommand(
            Path baseDir,
            PluginRegistry pluginRegistry,
            @Nullable URLClassLoader pluginClassLoader,
            @Nullable String nameFilter,
            boolean colorEnabled) {
        this.baseDir = baseDir;
        this.pluginRegistry = pluginRegistry;
        this.pluginClassLoader = pluginClassLoader;
        this.nameFilter = nameFilter;
        this.colorEnabled = colorEnabled;
    }

    @Override
    public int execute() {
        try {
            // 1. ExecutionContext をロード（設定、環境、グラフを含む）
            ExecutionContext context =
                    ExecutionContext.load(baseDir, pluginRegistry, Collections.emptyMap());
            ProjectConfig projectConfig = context.config().getConfigMapping(ProjectConfig.class);

            // 2. ジェネレーター設定を取得
            List<ProjectConfig.GeneratorSection> generators =
                    projectConfig.generators().orElse(Collections.emptyList());

            if (generators.isEmpty()) {
                System.out.println("No generators configured.");
                return 0;
            }

            // 3. GeneratorRegistry を初期化
            GeneratorRegistry generatorRegistry = new GeneratorRegistry();
            generatorRegistry.loadFromClasspath();
            if (pluginClassLoader != null) {
                generatorRegistry.loadFromClassLoader(pluginClassLoader);
            }
            generatorRegistry.loadFromDirectory(baseDir.resolve("plugins"));

            // 4. GeneratorExecutor で実行
            GeneratorExecutor executor = new GeneratorExecutor(generatorRegistry);
            executor.executeAll(
                    generators,
                    context.environments(),
                    context.graph(),
                    context.createHistoryRepository(),
                    baseDir,
                    nameFilter);

            printSuccess("Generation complete.");
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private void printSuccess(String message) {
        if (colorEnabled) {
            System.out.println(AnsiColor.green(message));
        } else {
            System.out.println(message);
        }
    }
}
