package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.core.config.ConfigValidator;
import io.github.kakusuke.migraphe.core.config.ConfigValidator.ValidationOutput;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.net.URLClassLoader;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Gradle task that validates the Migraphe configuration files offline.
 *
 * <p>Registered as {@code migrapheValidate} by {@link MigrapheGradlePlugin}, the task runs the
 * {@link ConfigValidator} over the configuration rooted at {@link #getBaseDir()} without requiring
 * any database connection, reporting each error and failing the build if validation does not pass.
 */
@DisableCachingByDefault(
        because = "Migraphe tasks have side effects and their output cannot be cached")
public abstract class MigrapheValidateTask extends AbstractMigrapheTask {

    /**
     * Task action that validates the configuration files.
     *
     * <p>Builds a {@link PluginRegistry} from the plugin classpath, runs the {@link
     * ConfigValidator}, prints any errors, and reports success or failure. The plugin class loader
     * is closed afterwards.
     *
     * @throws GradleException if validation fails with one or more errors
     */
    @TaskAction
    public void validate() {
        getLogger().lifecycle("Validation");
        getLogger().lifecycle("==========");
        getLogger().lifecycle("");

        URLClassLoader pluginClassLoader = createPluginClassLoader();
        try (PluginRegistry registry = createPluginRegistry(pluginClassLoader)) {
            ConfigValidator validator = new ConfigValidator(registry);
            ValidationOutput result =
                    validator.validate(
                            getBaseDir().get().getAsFile().toPath(), getEnv().getOrNull());

            for (String error : result.errors()) {
                getLogger().error("  × {}", error);
            }

            if (result.isValid()) {
                getLogger().lifecycle("");
                getLogger().lifecycle("Validation successful.");
            } else {
                getLogger().lifecycle("");
                int errorCount = result.errors().size();
                throw new GradleException(
                        "Validation failed with "
                                + errorCount
                                + " error"
                                + (errorCount == 1 ? "" : "s")
                                + ".");
            }
        } finally {
            closePluginClassLoader(pluginClassLoader);
        }
    }

    /** Creates the task and marks it as never up to date, since it has side effects. */
    public MigrapheValidateTask() {
        getOutputs().upToDateWhen(task -> false);
    }
}
