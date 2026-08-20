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
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;
import org.jspecify.annotations.Nullable;

/**
 * Common base class for all Migraphe Gradle tasks.
 *
 * <p>Concrete subclasses ({@link MigrapheUpTask}, {@link MigrapheDownTask}, {@link
 * MigrapheStatusTask}, {@link MigrapheValidateTask} and {@link MigrapheGenerateTask}) inherit the
 * shared task inputs — the configuration {@linkplain #getBaseDir() base directory}, the {@linkplain
 * #getVariables() variable map} and the {@linkplain #getPluginClasspath() plugin classpath} — and
 * the helper methods used to build a {@link PluginRegistry} and load an {@link ExecutionContext}
 * from them.
 *
 * <p>The class is annotated {@link DisableCachingByDefault} because Migraphe tasks have side
 * effects (they touch databases and history state) and their output cannot meaningfully be cached.
 */
@DisableCachingByDefault(
        because = "Migraphe tasks have side effects and their output cannot be cached")
public abstract class AbstractMigrapheTask extends DefaultTask {

    /** Constructor for use by subclasses. */
    protected AbstractMigrapheTask() {}

    /**
     * Returns the base directory containing the Migraphe configuration files.
     *
     * <p>Tracked as a relative-path-sensitive input directory so configuration changes invalidate
     * the task.
     *
     * @return the configurable base-directory property
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getBaseDir();

    /**
     * Returns the variable map injected into the SmallRye configuration to resolve {@code ${VAR}}
     * references in the YAML configuration.
     *
     * @return the configurable variable map property
     */
    @Input
    public abstract MapProperty<String, String> getVariables();

    /**
     * Returns the deployment-environment name whose {@code environments/<env>.yaml} overlay is
     * applied on top of the base configuration.
     *
     * <p>Settable in the {@code migraphe { ... }} block, via {@code -Pmigraphe.env=...}, or with
     * the {@code --env} command-line option. When absent, only the base configuration is used.
     *
     * @return the optional environment-name property
     */
    @Input
    @Optional
    public abstract Property<String> getEnv();

    /**
     * Sets the {@linkplain #getEnv() environment} from the {@code --env} command-line option.
     *
     * @param env the deployment-environment name to overlay
     */
    @Option(option = "env", description = "Environment overlay to apply (environments/<env>.yaml)")
    public void setEnvOption(String env) {
        getEnv().set(env);
    }

    /**
     * Returns the resolved JAR files of the {@code migraphePlugin} configuration, from which
     * database/generator plugins are loaded at runtime.
     *
     * @return the configurable plugin classpath file collection
     */
    @Classpath
    public abstract ConfigurableFileCollection getPluginClasspath();

    /**
     * Creates a {@link URLClassLoader} over the JARs of the {@code migraphePlugin} configuration,
     * parented to the class loader of {@link MigraphePlugin}.
     *
     * <p>Files that cannot be converted to a URL are skipped with a warning. The caller is
     * responsible for closing the returned loader (see {@link #closePluginClassLoader} and {@link
     * #withExecutionContext}).
     *
     * @return a class loader over the plugin JARs, or {@code null} if the plugin classpath is empty
     */
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

    /**
     * Creates and populates a {@link PluginRegistry}.
     *
     * <p>Plugins are first discovered from the current classpath (which covers the test and
     * in-process cases) and then, if a plugin class loader is supplied, from the {@code
     * migraphePlugin} JARs.
     *
     * @param pluginClassLoader the class loader over the plugin JARs, or {@code null} to load only
     *     from the current classpath
     * @return a registry populated with all discovered plugins
     */
    protected PluginRegistry createPluginRegistry(@Nullable URLClassLoader pluginClassLoader) {
        PluginRegistry registry = new PluginRegistry();

        // Load from the current classpath (e.g. during tests).
        registry.loadFromClasspath();

        // Load from the migraphePlugin configuration JARs.
        if (pluginClassLoader != null) {
            registry.loadFromClassLoader(pluginClassLoader);
        }

        return registry;
    }

    /**
     * Loads an {@link ExecutionContext} using a freshly created plugin class loader.
     *
     * <p>Note that the class loader created internally is not closed by this method; prefer {@link
     * #withExecutionContext(Consumer)} when the loader's lifetime should be bounded.
     *
     * @return the loaded execution context
     */
    protected ExecutionContext loadExecutionContext() {
        return loadExecutionContext(createPluginClassLoader());
    }

    /**
     * Loads an {@link ExecutionContext}, sharing an externally created plugin class loader.
     *
     * <p>Builds a {@link PluginRegistry} from the given loader and reads the configuration rooted
     * at {@link #getBaseDir()} using the {@link #getVariables() variables}.
     *
     * @param pluginClassLoader the class loader over the plugin JARs, or {@code null} to load
     *     plugins only from the current classpath
     * @return the loaded execution context
     */
    protected ExecutionContext loadExecutionContext(@Nullable URLClassLoader pluginClassLoader) {
        PluginRegistry registry = createPluginRegistry(pluginClassLoader);
        return ExecutionContext.load(
                getBaseDir().get().getAsFile().toPath(),
                registry,
                getEnv().getOrNull(),
                getVariables().get());
    }

    /**
     * Loads an {@link ExecutionContext} and runs the given action with it, then closes the plugin
     * class loader in a {@code finally} block.
     *
     * <p>This helper prevents class-loader resource leaks across runs in the long-lived Gradle
     * daemon. The class loader (and therefore the plugin classes it defines) must not be retained
     * beyond the scope of {@code action}.
     *
     * @param action the action to execute with the loaded execution context
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

    /**
     * Closes the given plugin class loader, ignoring a {@code null} argument and logging (rather
     * than propagating) any {@link IOException} raised while closing.
     *
     * @param pluginClassLoader the class loader to close, or {@code null} to do nothing
     */
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
