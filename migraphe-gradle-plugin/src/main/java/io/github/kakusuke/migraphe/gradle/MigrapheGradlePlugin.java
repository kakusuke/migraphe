package io.github.kakusuke.migraphe.gradle;

import java.util.Collections;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

/**
 * Entry point of the Migraphe Gradle plugin.
 *
 * <p>Applied via {@code apply plugin: "io.github.kakusuke.migraphe"} (or the corresponding {@code
 * plugins { }} block), this {@link Plugin} bootstraps the Migraphe integration for a Gradle
 * project. On application it:
 *
 * <ul>
 *   <li>creates the {@link MigrapheExtension} ({@code migraphe { ... }}) and installs default
 *       conventions for {@link MigrapheExtension#getBaseDir() baseDir} and {@link
 *       MigrapheExtension#getVariables() variables};
 *   <li>creates the resolvable {@code migraphePlugin} configuration, used to declare
 *       database/plugin JAR dependencies whose classpath is handed to every task;
 *   <li>lazily registers the {@code migrapheValidate}, {@code migrapheStatus}, {@code
 *       migrapheGenerate}, {@code migrapheUp} and {@code migrapheDown} tasks in the {@code
 *       migraphe} group, wiring the extension properties and the plugin classpath onto each.
 * </ul>
 *
 * <p>For tasks that also accept command-line options, configuration-time fallbacks are read from
 * Gradle project properties (for example {@code -Pmigraphe.up.target=...}) and applied as task
 * conventions, so values can be supplied either via {@code --option} or via {@code -P} properties.
 */
public class MigrapheGradlePlugin implements Plugin<Project> {

    /** Creates a new {@code MigrapheGradlePlugin}. */
    public MigrapheGradlePlugin() {}

    /**
     * Applies the plugin to the given project, creating the {@code migraphe} extension, the {@code
     * migraphePlugin} configuration and the five Migraphe tasks.
     *
     * @param project the Gradle project the plugin is applied to
     */
    @Override
    public void apply(Project project) {
        // 1. Create the extension and install default conventions.
        MigrapheExtension extension =
                project.getExtensions().create("migraphe", MigrapheExtension.class);
        extension.getBaseDir().convention(project.getLayout().getProjectDirectory());
        extension.getVariables().convention(Collections.emptyMap());

        // 2. Custom configuration.
        Configuration migraphePluginConfig =
                project.getConfigurations()
                        .create(
                                "migraphePlugin",
                                config -> {
                                    config.setDescription("Migraphe database plugin dependencies");
                                    config.setCanBeConsumed(false);
                                    config.setCanBeResolved(true);
                                });

        // 3. Register tasks (lazily).
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
                            applyEnvSources(project, extension, task);
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
                            applyEnvSources(project, extension, task);
                        });

        project.getTasks()
                .register(
                        "migrapheGenerate",
                        MigrapheGenerateTask.class,
                        task -> {
                            task.setDescription("Execute code generators");
                            task.setGroup("migraphe");
                            task.getBaseDir().set(extension.getBaseDir());
                            task.getVariables().set(extension.getVariables());
                            task.getPluginClasspath().from(migraphePluginConfig);
                            applyEnvSources(project, extension, task);
                            // Fallback from -P properties (at configuration time).
                            Object nameProp = project.findProperty("migraphe.generate.name");
                            if (nameProp != null) {
                                task.getGeneratorName().convention(nameProp.toString());
                            }
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
                            applyEnvSources(project, extension, task);
                            // Fallback from -P properties (at configuration time).
                            Object targetProp = project.findProperty("migraphe.up.target");
                            if (targetProp != null) {
                                task.getTarget().convention(targetProp.toString());
                            }
                            Object dryRunProp = project.findProperty("migraphe.up.dryRun");
                            if ("true".equals(String.valueOf(dryRunProp))) {
                                task.getDryRun().convention(true);
                            }
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
                            applyEnvSources(project, extension, task);
                            // Fallback from -P properties (at configuration time).
                            Object targetProp = project.findProperty("migraphe.down.target");
                            if (targetProp != null) {
                                task.getTarget().convention(targetProp.toString());
                            }
                            Object allProp = project.findProperty("migraphe.down.all");
                            if ("true".equals(String.valueOf(allProp))) {
                                task.getAll().convention(true);
                            }
                            Object dryRunProp = project.findProperty("migraphe.down.dryRun");
                            if ("true".equals(String.valueOf(dryRunProp))) {
                                task.getDryRun().convention(true);
                            }
                        });
    }

    /**
     * Wires the environment-overlay name onto a task from the extension and {@code -P} properties.
     *
     * <p>Precedence, lowest first: the {@code migraphe { env = ... }} block, then {@code
     * -Pmigraphe.env=...}, then the task's {@code --env} command-line option (applied by Gradle
     * after configuration). The extension is bound as a <em>convention</em> rather than with {@code
     * set} so that an unset extension property still leaves room for the {@code -P} fallback.
     *
     * @param project the project whose properties supply the {@code -P} fallback
     * @param extension the Migraphe extension holding the build-script value
     * @param task the task to configure
     */
    private static void applyEnvSources(
            Project project, MigrapheExtension extension, AbstractMigrapheTask task) {
        task.getEnv().convention(extension.getEnv());
        Object envProp = project.findProperty("migraphe.env");
        if (envProp != null) {
            task.getEnv().set(envProp.toString());
        }
    }
}
