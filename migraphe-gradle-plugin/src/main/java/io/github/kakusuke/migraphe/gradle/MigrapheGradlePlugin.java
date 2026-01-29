package io.github.kakusuke.migraphe.gradle;

import java.util.Collections;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

/** migraphe Gradle プラグインのエントリポイント。 */
public class MigrapheGradlePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // 1. Extension 作成 + デフォルト設定
        MigrapheExtension extension =
                project.getExtensions().create("migraphe", MigrapheExtension.class);
        extension.getBaseDir().convention(project.getLayout().getProjectDirectory());
        extension.getVariables().convention(Collections.emptyMap());

        // 2. カスタム configuration
        Configuration migraphePluginConfig =
                project.getConfigurations()
                        .create(
                                "migraphePlugin",
                                config -> {
                                    config.setDescription("Migraphe database plugin dependencies");
                                    config.setCanBeConsumed(false);
                                    config.setCanBeResolved(true);
                                });

        // 3. タスク登録（lazy）
        project.getTasks()
                .register(
                        "migrapheValidate",
                        MigrapheValidateTask.class,
                        task -> {
                            task.setDescription("Validate migraphe configuration files (offline)");
                            task.setGroup("migraphe");
                            task.getBaseDir().set(extension.getBaseDir());
                            task.getVariables().set(extension.getVariables());
                            task.getPluginClasspath().from(migraphePluginConfig);
                        });

        project.getTasks()
                .register(
                        "migrapheStatus",
                        MigrapheStatusTask.class,
                        task -> {
                            task.setDescription("Show migration execution status");
                            task.setGroup("migraphe");
                            task.getBaseDir().set(extension.getBaseDir());
                            task.getVariables().set(extension.getVariables());
                            task.getPluginClasspath().from(migraphePluginConfig);
                        });

        project.getTasks()
                .register(
                        "migrapheUp",
                        MigrapheUpTask.class,
                        task -> {
                            task.setDescription("Execute forward (UP) migrations");
                            task.setGroup("migraphe");
                            task.getBaseDir().set(extension.getBaseDir());
                            task.getVariables().set(extension.getVariables());
                            task.getPluginClasspath().from(migraphePluginConfig);
                        });

        project.getTasks()
                .register(
                        "migrapheDown",
                        MigrapheDownTask.class,
                        task -> {
                            task.setDescription("Execute rollback (DOWN) migrations");
                            task.setGroup("migraphe");
                            task.getBaseDir().set(extension.getBaseDir());
                            task.getVariables().set(extension.getVariables());
                            task.getPluginClasspath().from(migraphePluginConfig);
                        });
    }
}
