package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.core.config.ConfigValidator;
import io.github.kakusuke.migraphe.core.config.ConfigValidator.ValidationOutput;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.net.URLClassLoader;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** 設定ファイルをオフラインで検証する Gradle タスク。DB 接続不要。 */
@DisableCachingByDefault(because = "migraphe タスクは副作用を伴い出力をキャッシュできない")
public abstract class MigrapheValidateTask extends AbstractMigrapheTask {

    @TaskAction
    public void validate() {
        getLogger().lifecycle("Validation");
        getLogger().lifecycle("==========");
        getLogger().lifecycle("");

        URLClassLoader pluginClassLoader = createPluginClassLoader();
        try (PluginRegistry registry = createPluginRegistry(pluginClassLoader)) {
            ConfigValidator validator = new ConfigValidator(registry);
            ValidationOutput result = validator.validate(getBaseDir().get().getAsFile().toPath());

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

    /** 副作用のあるタスクはキャッシュしない。 */
    public MigrapheValidateTask() {
        getOutputs().upToDateWhen(task -> false);
    }
}
