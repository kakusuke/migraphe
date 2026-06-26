package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.core.config.ProjectConfig;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.generator.GeneratorExecutor;
import io.github.kakusuke.migraphe.core.generator.GeneratorRegistry;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;
import org.jspecify.annotations.Nullable;

/**
 * Gradle task that runs the configured code generators.
 *
 * <p>Registered as {@code migrapheGenerate} by {@link MigrapheGradlePlugin}, the task loads the
 * generator plugins (from the classpath, the {@code migraphePlugin} configuration, and the
 * project's {@code plugins} directory) and runs every generator declared in the project
 * configuration through a {@link GeneratorExecutor}. An optional {@linkplain #getGeneratorName()
 * name} restricts execution to a single generator.
 */
@DisableCachingByDefault(
        because = "Migraphe tasks have side effects and their output cannot be cached")
public abstract class MigrapheGenerateTask extends AbstractMigrapheTask {

    /**
     * Returns the optional name of the single generator to run. When absent, all configured
     * generators are executed.
     *
     * @return the optional generator name property
     */
    @Input
    @Optional
    public abstract Property<String> getGeneratorName();

    /**
     * Sets the {@linkplain #getGeneratorName() generator name} from the {@code --name} command-line
     * option.
     *
     * @param name the name of the generator to run
     */
    @Option(option = "name", description = "Generator name to execute")
    public void setGeneratorNameOption(String name) {
        getGeneratorName().set(name);
    }

    /**
     * Task action that runs the configured generators.
     *
     * <p>Loads the execution context and generator registry, then executes either all configured
     * generators or only the one matching {@link #getGeneratorName()}. The plugin class loader is
     * closed afterwards.
     *
     * @throws GradleException if a generator fails with an {@link IllegalArgumentException} (for
     *     example, an unknown generator name)
     */
    @TaskAction
    public void generate() {
        URLClassLoader pluginClassLoader = createPluginClassLoader();
        try (GeneratorRegistry generatorRegistry = new GeneratorRegistry()) {
            ExecutionContext context = loadExecutionContext(pluginClassLoader);

            generatorRegistry.loadFromClasspath();
            if (pluginClassLoader != null) {
                generatorRegistry.loadFromClassLoader(pluginClassLoader);
            }
            generatorRegistry.loadFromDirectory(context.scanRoot().resolve("plugins"));

            ProjectConfig projectConfig = context.config().getConfigMapping(ProjectConfig.class);
            List<ProjectConfig.GeneratorSection> generators =
                    projectConfig.generators().orElse(Collections.emptyList());

            @Nullable String nameFilter = getGeneratorName().getOrNull();

            if (generators.isEmpty()) {
                getLogger().lifecycle("No generators configured.");
                return;
            }

            getLogger().lifecycle("Generate");
            getLogger().lifecycle("========");
            getLogger().lifecycle("");

            try {
                GeneratorExecutor executor = new GeneratorExecutor(generatorRegistry);
                executor.executeAll(
                        generators,
                        context.environments(),
                        context.graph(),
                        context.createHistoryRepository(),
                        context.config(),
                        context.baseDir(),
                        nameFilter);
                getLogger().lifecycle("");
                getLogger().lifecycle("Generation completed successfully.");
            } catch (IllegalArgumentException e) {
                throw new GradleException(String.valueOf(e.getMessage()), e);
            }
        } finally {
            closePluginClassLoader(pluginClassLoader);
        }
    }

    /** Creates the task and marks it as never up to date, since it has side effects. */
    public MigrapheGenerateTask() {
        getOutputs().upToDateWhen(task -> false);
    }
}
