package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.cli.util.AnsiColor;
import io.github.kakusuke.migraphe.core.config.ConfigLoader;
import io.github.kakusuke.migraphe.core.config.ProjectConfig;
import io.github.kakusuke.migraphe.core.factory.EnvironmentFactory;
import io.github.kakusuke.migraphe.core.generator.GeneratorExecutor;
import io.github.kakusuke.migraphe.core.generator.GeneratorRegistry;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import io.smallrye.config.SmallRyeConfig;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** ジェネレーターを実行するコマンド。 */
public class GenerateCommand implements Command {

    private final Path baseDir;
    private final PluginRegistry pluginRegistry;
    private final @Nullable String nameFilter;
    private final boolean colorEnabled;

    public GenerateCommand(
            Path baseDir, PluginRegistry pluginRegistry, @Nullable String nameFilter) {
        this(baseDir, pluginRegistry, nameFilter, AnsiColor.isColorEnabled());
    }

    /** テスト用コンストラクタ。 */
    public GenerateCommand(
            Path baseDir,
            PluginRegistry pluginRegistry,
            @Nullable String nameFilter,
            boolean colorEnabled) {
        this.baseDir = baseDir;
        this.pluginRegistry = pluginRegistry;
        this.nameFilter = nameFilter;
        this.colorEnabled = colorEnabled;
    }

    @Override
    public int execute() {
        try {
            // 1. 設定をロード
            ConfigLoader configLoader = new ConfigLoader();
            SmallRyeConfig config = configLoader.load(baseDir, Collections.emptyMap());
            ProjectConfig projectConfig = config.getConfigMapping(ProjectConfig.class);

            // 2. ジェネレーター設定を取得
            List<ProjectConfig.GeneratorSection> generators =
                    projectConfig.generators().orElse(Collections.emptyList());

            if (generators.isEmpty()) {
                System.out.println("No generators configured.");
                return 0;
            }

            // 3. 環境をロード
            Map<String, io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition>
                    environmentDefinitions =
                            configLoader.loadEnvironmentDefinitions(config, pluginRegistry);
            EnvironmentFactory environmentFactory = new EnvironmentFactory(pluginRegistry);
            Map<String, Environment> environments =
                    environmentFactory.createEnvironments(environmentDefinitions);

            // 4. GeneratorRegistry を初期化
            GeneratorRegistry generatorRegistry = new GeneratorRegistry();
            generatorRegistry.loadFromClasspath();

            // 5. GeneratorExecutor で実行
            GeneratorExecutor executor = new GeneratorExecutor(generatorRegistry);
            executor.executeAll(generators, environments, baseDir, nameFilter);

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
