package io.github.kakusuke.migraphe.gradle;

import io.github.kakusuke.migraphe.core.config.ProjectConfig;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.generator.GeneratorExecutor;
import io.github.kakusuke.migraphe.core.generator.GeneratorRegistry;
import java.util.Collections;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.jspecify.annotations.Nullable;

/** ジェネレーターを実行する Gradle タスク。 */
public abstract class MigrapheGenerateTask extends AbstractMigrapheTask {

    /** 実行するジェネレーター名。 */
    @Input
    @Optional
    public abstract Property<String> getGeneratorName();

    @Option(option = "name", description = "Generator name to execute")
    public void setGeneratorNameOption(String name) {
        getGeneratorName().set(name);
    }

    @TaskAction
    public void generate() {
        ExecutionContext context = loadExecutionContext();

        // GeneratorRegistry を作成してプラグインをロード
        GeneratorRegistry generatorRegistry = new GeneratorRegistry();
        generatorRegistry.loadFromClasspath();

        // ProjectConfig からジェネレーター設定を取得
        ProjectConfig projectConfig = context.config().getConfigMapping(ProjectConfig.class);
        List<ProjectConfig.GeneratorSection> generators =
                projectConfig.generators().orElse(Collections.emptyList());

        @Nullable String nameFilter = getGeneratorName().getOrElse(null);

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
                    context.baseDir(),
                    nameFilter);
            getLogger().lifecycle("");
            getLogger().lifecycle("Generation completed successfully.");
        } catch (IllegalArgumentException e) {
            throw new GradleException(e.getMessage(), e);
        }
    }

    /** 副作用のあるタスクはキャッシュしない。 */
    public MigrapheGenerateTask() {
        getOutputs().upToDateWhen(task -> false);
    }
}
