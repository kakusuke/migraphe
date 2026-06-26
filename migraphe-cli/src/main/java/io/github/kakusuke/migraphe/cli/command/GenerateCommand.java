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

/**
 * The {@code generate} command, which runs the configured generators.
 *
 * <p>Loads the project configuration, gathers the {@code generators:} section, and executes each
 * generator (for example schema documentation or migration-tree output) through a {@link
 * GeneratorExecutor} backed by a {@link GeneratorRegistry}. The registry pulls source and output
 * plugins from the classpath, the Maven-resolved plugin class loader, and the {@code plugins/}
 * directory. An optional name filter restricts execution to a single generator.
 */
public class GenerateCommand implements Command {

    private final Path baseDir;
    private final PluginRegistry pluginRegistry;
    private final @Nullable URLClassLoader pluginClassLoader;
    private final @Nullable String nameFilter;
    private final Path pluginsDir;
    private final boolean colorEnabled;

    /**
     * Creates the generate command with color support auto-detected.
     *
     * @param baseDir the project base directory containing {@code migraphe.yaml}
     * @param pluginRegistry the registry of loaded migration plugins, used to build the execution
     *     context
     * @param pluginClassLoader the class loader holding Maven-resolved generator plugins, or {@code
     *     null} if no external plugins were resolved
     * @param nameFilter the name of the single generator to run, or {@code null} to run all
     *     configured generators
     * @param pluginsDir the {@code plugins/} directory scanned for additional generator plugins
     */
    public GenerateCommand(
            Path baseDir,
            PluginRegistry pluginRegistry,
            @Nullable URLClassLoader pluginClassLoader,
            @Nullable String nameFilter,
            Path pluginsDir) {
        this(
                baseDir,
                pluginRegistry,
                pluginClassLoader,
                nameFilter,
                pluginsDir,
                AnsiColor.isColorEnabled());
    }

    /**
     * Full constructor exposing the color flag, intended primarily for testing.
     *
     * @param baseDir the project base directory containing {@code migraphe.yaml}
     * @param pluginRegistry the registry of loaded migration plugins, used to build the execution
     *     context
     * @param pluginClassLoader the class loader holding Maven-resolved generator plugins, or {@code
     *     null} if no external plugins were resolved
     * @param nameFilter the name of the single generator to run, or {@code null} to run all
     *     configured generators
     * @param pluginsDir the {@code plugins/} directory scanned for additional generator plugins
     * @param colorEnabled {@code true} to colorize console output
     */
    public GenerateCommand(
            Path baseDir,
            PluginRegistry pluginRegistry,
            @Nullable URLClassLoader pluginClassLoader,
            @Nullable String nameFilter,
            Path pluginsDir,
            boolean colorEnabled) {
        this.baseDir = baseDir;
        this.pluginRegistry = pluginRegistry;
        this.pluginClassLoader = pluginClassLoader;
        this.nameFilter = nameFilter;
        this.pluginsDir = pluginsDir;
        this.colorEnabled = colorEnabled;
    }

    @Override
    public int execute() {
        try {
            ExecutionContext context =
                    ExecutionContext.load(baseDir, pluginRegistry, Collections.emptyMap());
            ProjectConfig projectConfig = context.config().getConfigMapping(ProjectConfig.class);

            List<ProjectConfig.GeneratorSection> generators =
                    projectConfig.generators().orElse(Collections.emptyList());

            if (generators.isEmpty()) {
                System.out.println("No generators configured.");
                return 0;
            }

            try (GeneratorRegistry generatorRegistry = new GeneratorRegistry()) {
                generatorRegistry.loadFromClasspath();
                if (pluginClassLoader != null) {
                    generatorRegistry.loadFromClassLoader(pluginClassLoader);
                }
                generatorRegistry.loadFromDirectory(pluginsDir);

                GeneratorExecutor executor = new GeneratorExecutor(generatorRegistry);
                executor.executeAll(
                        generators,
                        context.environments(),
                        context.graph(),
                        context.createHistoryRepository(),
                        context.config(),
                        baseDir,
                        nameFilter);
            }

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
