package io.github.kakusuke.migraphe.core.config;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

/**
 * Loads Migraphe's multi-file YAML configuration into a {@link SmallRyeConfig}.
 *
 * <p>This is the central entry point of the {@code config} package. It discovers the configuration
 * files via {@link YamlFileScanner}, merges them through {@link MultiFileYamlConfigSource}, and
 * stacks the additional config sources that drive {@code ${...}} expansion. The precedence (by
 * ordinal, highest wins) is:
 *
 * <ol>
 *   <li>explicit {@code variables} (ordinal 600)
 *   <li>environment-override file {@code environments/<envName>.yaml} (ordinal 500)
 *   <li>system properties, referenced as {@code ${name}} (ordinal 400)
 *   <li>OS environment, namespaced under {@code env.} and referenced as {@code ${env.NAME}}
 *       (ordinal 300)
 *   <li>the merged YAML files (ordinal 100)
 * </ol>
 *
 * <p>Only {@link ProjectConfig} is bound via {@code withMapping}; the dynamically-keyed {@code
 * target.*} and {@code task.*} entries are read programmatically through {@link
 * #loadEnvironmentDefinitions} and {@link #loadTaskDefinitions}, which map each entry onto the
 * relevant plugin's definition type via {@link PrefixedConfigSource}.
 */
public class ConfigLoader {

    /** Creates a new {@code ConfigLoader}. */
    public ConfigLoader() {}

    /**
     * Loads the configuration with no environment file and no extra variables.
     *
     * @param baseDir the project root directory
     * @return the built config
     * @throws ConfigurationException if the configuration cannot be loaded
     */
    public SmallRyeConfig load(Path baseDir) {
        return loadConfig(baseDir, null, Collections.emptyMap());
    }

    /**
     * Loads the configuration with extra variables but no environment file.
     *
     * @param baseDir the project root directory
     * @param variables externally-supplied variables, injected at the highest precedence (ordinal
     *     600)
     * @return the built config
     * @throws ConfigurationException if the configuration cannot be loaded
     */
    public SmallRyeConfig load(Path baseDir, Map<String, String> variables) {
        return loadConfig(baseDir, null, variables);
    }

    /**
     * Loads the configuration, optionally applying a deployment-environment override file.
     *
     * @param baseDir the project root directory
     * @param envName the environment name; when {@code null} no {@code environments/*.yaml} file is
     *     loaded
     * @return the built config
     * @throws ConfigurationException if the configuration cannot be loaded
     */
    public SmallRyeConfig loadConfig(Path baseDir, @Nullable String envName) {
        return loadConfig(baseDir, envName, Collections.emptyMap());
    }

    /**
     * Loads the configuration, optionally applying an environment file and extra variables.
     *
     * <p>Discovers {@code migraphe.yaml}, resolves the scan root, scans targets and tasks, merges
     * them, and registers the environment file, system properties, OS environment and explicit
     * variables as additional sources (see the class documentation for the precedence). {@link
     * ProjectConfig} is bound as a mapping with unknown-property validation disabled.
     *
     * @param baseDir the project root directory
     * @param envName the environment name; when {@code null} no {@code environments/*.yaml} file is
     *     loaded
     * @param variables externally-supplied variables, injected at the highest precedence (ordinal
     *     600); ignored when empty
     * @return the built config
     * @throws ConfigurationException if {@code migraphe.yaml} is missing or an environment file
     *     cannot be read
     */
    public SmallRyeConfig loadConfig(
            Path baseDir, @Nullable String envName, Map<String, String> variables) {
        YamlFileScanner scanner = new YamlFileScanner();
        TaskIdGenerator idGenerator = new TaskIdGenerator();

        // 1. Discover the project config file (migraphe.yaml).
        Path projectConfigFile = scanner.findProjectConfig(baseDir);
        if (projectConfigFile == null) {
            throw new ConfigurationException(
                    "Project config file not found: " + baseDir.resolve("migraphe.yaml"));
        }

        // 2. Resolve the scan root and scan the target files (targets/*.yaml).
        Path scanRoot = resolveScanRoot(baseDir, projectConfigFile);
        List<Path> targetFiles = scanner.scanTargetFiles(scanRoot);

        // 3. Scan the task files (tasks/**/*.yaml) and generate their task ids.
        List<Path> taskFilePaths = scanner.scanTaskFiles(scanRoot);
        Map<NodeId, Path> taskFiles = new HashMap<>();
        for (Path taskFile : taskFilePaths) {
            NodeId taskId = idGenerator.generateTaskId(scanRoot, taskFile);
            taskFiles.put(taskId, taskFile);
        }

        // 4. Build the merged MultiFileYamlConfigSource.
        MultiFileYamlConfigSource multiFileSource =
                new MultiFileYamlConfigSource(projectConfigFile, targetFiles, taskFiles);

        // 5. Build the SmallRyeConfigBuilder.
        // Note: only ProjectConfig is bound via withMapping. TargetConfig and TaskConfig live under
        // dynamic prefixes and must be retrieved programmatically.
        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder().addDefaultInterceptors();

        // 6. Load the environment override file if present (ordinal 500).
        Path envFile = envName != null ? scanner.findEnvironmentFile(scanRoot, envName) : null;

        if (envFile != null) {
            try {
                YamlConfigSource envSource = new YamlConfigSource(envFile.toUri().toURL(), 500);
                // Register the environment file and the MultiFileYamlConfigSource together.
                builder.withSources(envSource, multiFileSource);
            } catch (IOException e) {
                throw new ConfigurationException("Failed to load environment file: " + envFile, e);
            }
        } else {
            // No environment file: register only the MultiFileYamlConfigSource.
            builder.withSources(multiFileSource);
        }

        // 7. Register System.getenv() under env.<NAME> keys (ordinal 300): referenced as
        // ${env.VAR}.
        Map<String, String> envVars = new HashMap<>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            envVars.put("env." + entry.getKey(), entry.getValue());
        }
        builder.withSources(new MapConfigSource(envVars, 300));

        // 8. Register system properties under their raw keys (ordinal 400): referenced as ${name}.
        Map<String, String> sysProps = new HashMap<>();
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            if (entry.getKey() instanceof String k && entry.getValue() instanceof String v) {
                sysProps.put(k, v);
            }
        }
        builder.withSources(new MapConfigSource(sysProps, 400));

        // 9. Register explicit variables, if any, at the highest precedence (ordinal 600).
        if (!variables.isEmpty()) {
            builder.withSources(new MapConfigSource(variables));
        }

        // 10. Configure the mapping and validation.
        builder.withMapping(ProjectConfig.class).withValidateUnknown(false); // allow unmapped keys

        return builder.build();
    }

    /**
     * Loads every {@link TaskDefinition} from the {@code tasks/} directory.
     *
     * <p>For each task file, the {@code target} field selects a target whose {@code type}
     * identifies the plugin; the file is then mapped onto that plugin's task-definition type.
     *
     * @param baseDir the project base directory (the scan root is resolved internally from {@code
     *     project.scan-root})
     * @param mainConfig the merged main config, used to look up target types
     * @param pluginRegistry the registry used to resolve plugins by type
     * @return a map from task {@link NodeId} to its loaded {@link TaskDefinition}, in scan order
     * @throws ConfigurationException if a task file cannot be loaded or its target type is unknown
     */
    public Map<NodeId, TaskDefinition<?>> loadTaskDefinitions(
            Path baseDir, SmallRyeConfig mainConfig, PluginRegistry pluginRegistry) {

        YamlFileScanner scanner = new YamlFileScanner();
        TaskIdGenerator idGenerator = new TaskIdGenerator();
        Map<NodeId, TaskDefinition<?>> taskDefinitions = new LinkedHashMap<>();

        // Scan all YAML files under the tasks/ directory.
        Path projectConfigFile = scanner.findProjectConfig(baseDir);
        Path scanRoot =
                projectConfigFile != null ? resolveScanRoot(baseDir, projectConfigFile) : baseDir;
        List<Path> taskFiles = scanner.scanTaskFiles(scanRoot);

        for (Path taskFile : taskFiles) {
            NodeId nodeId = idGenerator.generateTaskId(scanRoot, taskFile);
            TaskDefinition<?> taskDef = loadTaskDefinition(taskFile, mainConfig, pluginRegistry);
            taskDefinitions.put(nodeId, taskDef);
        }

        return taskDefinitions;
    }

    /**
     * Loads an {@link EnvironmentDefinition} for every target.
     *
     * <p>Target ids are discovered from the {@code target.*} keys of the merged config; each
     * target's {@code type} identifies the plugin, and the target's properties are mapped onto that
     * plugin's environment-definition type.
     *
     * @param mainConfig the merged main config containing the {@code target.*} entries
     * @param pluginRegistry the registry used to resolve plugins by type
     * @return a map from target id to its loaded {@link EnvironmentDefinition}
     * @throws ConfigurationException if a target's type is missing or unknown
     */
    public Map<String, EnvironmentDefinition> loadEnvironmentDefinitions(
            SmallRyeConfig mainConfig, PluginRegistry pluginRegistry) {

        Map<String, EnvironmentDefinition> environmentDefinitions = new LinkedHashMap<>();

        // 1. Extract target ids from the target.* prefixed properties.
        Set<String> targetIds = extractTargetIds(mainConfig);

        for (String targetId : targetIds) {
            EnvironmentDefinition envDef =
                    loadEnvironmentDefinition(targetId, mainConfig, pluginRegistry);
            environmentDefinitions.put(targetId, envDef);
        }

        return environmentDefinitions;
    }

    /**
     * Loads the {@link EnvironmentDefinition} for a single target.
     *
     * <p>Reads {@code target.<targetId>.type}, resolves the corresponding plugin, then maps the
     * target's properties (exposed prefix-stripped via {@link PrefixedConfigSource}) onto the
     * plugin's {@link MigraphePlugin#environmentDefinitionClass()}.
     *
     * @param targetId the target id
     * @param mainConfig the merged main config containing this target's properties
     * @param pluginRegistry the registry used to resolve the plugin by type
     * @return the loaded environment definition
     * @throws ConfigurationException if the target's {@code type} is missing
     */
    public EnvironmentDefinition loadEnvironmentDefinition(
            String targetId, SmallRyeConfig mainConfig, PluginRegistry pluginRegistry) {

        String prefix = "target." + targetId + ".";

        // 1. Read the type.
        String type = mainConfig.getValue(prefix + "type", String.class);
        if (type == null) {
            throw new ConfigurationException("Target type not found for target: " + targetId);
        }

        // 2. Resolve the plugin.
        MigraphePlugin<?> plugin = pluginRegistry.getRequiredPlugin(type);

        // 3. Map onto the plugin's EnvironmentDefinition class.
        // Build it from a prefix-stripped view of this target's properties.
        SmallRyeConfig envConfig =
                new SmallRyeConfigBuilder()
                        .withSources(new PrefixedConfigSource(mainConfig, prefix))
                        .withMapping(plugin.environmentDefinitionClass())
                        .withValidateUnknown(false)
                        .build();

        return envConfig.getConfigMapping(plugin.environmentDefinitionClass());
    }

    /**
     * Resolves the configuration scan root from {@code project.scan-root} in {@code migraphe.yaml}.
     *
     * <p>If {@code scan-root} is set, the result is that path resolved against {@code baseDir};
     * otherwise {@code baseDir} itself is returned. If {@code migraphe.yaml} is absent, {@code
     * baseDir} is returned unchanged.
     *
     * @param baseDir the project root directory
     * @return the resolved scan-root path
     */
    public Path resolveScanRoot(Path baseDir) {
        YamlFileScanner scanner = new YamlFileScanner();
        Path projectConfigFile = scanner.findProjectConfig(baseDir);
        if (projectConfigFile == null) {
            return baseDir;
        }
        return resolveScanRoot(baseDir, projectConfigFile);
    }

    /**
     * Resolves the scan root from a known {@code migraphe.yaml} path.
     *
     * <p>Loads only the project section, then resolves {@code project.scan-root} against {@code
     * baseDir}, falling back to {@code baseDir} when unspecified.
     *
     * @param baseDir the project root directory
     * @param projectConfigFile the path to {@code migraphe.yaml}
     * @return the resolved scan-root path
     */
    private Path resolveScanRoot(Path baseDir, Path projectConfigFile) {
        SmallRyeConfig projectOnlyConfig =
                new SmallRyeConfigBuilder()
                        .addDefaultInterceptors()
                        .withSources(
                                new MultiFileYamlConfigSource(
                                        projectConfigFile, List.of(), Map.of()))
                        .withMapping(ProjectConfig.class)
                        .withValidateUnknown(false)
                        .build();
        return projectOnlyConfig
                .getConfigMapping(ProjectConfig.class)
                .project()
                .scanRoot()
                .map(baseDir::resolve)
                .orElse(baseDir);
    }

    /**
     * Extracts the set of target ids from the {@code target.*} property names of the config.
     *
     * @param config the merged config to inspect
     * @return the distinct target ids found
     */
    private Set<String> extractTargetIds(SmallRyeConfig config) {
        Set<String> targetIds = new HashSet<>();

        StreamSupport.stream(config.getPropertyNames().spliterator(), false)
                .filter(name -> name.startsWith("target."))
                .map(name -> name.substring("target.".length()))
                .map(
                        name -> {
                            int dotIndex = name.indexOf('.');
                            return dotIndex >= 0 ? name.substring(0, dotIndex) : name;
                        })
                .forEach(targetIds::add);

        return targetIds;
    }

    /**
     * Loads a single {@link TaskDefinition} from a task file.
     *
     * <p>First reads just the {@code target} field, looks up that target's {@code type} in {@code
     * mainConfig}, resolves the plugin, then maps the file onto the plugin's task-definition type.
     *
     * @param taskFile the path to the task file
     * @param mainConfig the merged main config, used to look up the target's type
     * @param pluginRegistry the registry used to resolve the plugin by type
     * @return the loaded task definition
     * @throws ConfigurationException if the file cannot be read or its target type is unknown
     */
    public TaskDefinition<?> loadTaskDefinition(
            Path taskFile, SmallRyeConfig mainConfig, PluginRegistry pluginRegistry) {

        try {
            YamlConfigSource taskSource = new YamlConfigSource(taskFile.toUri().toURL());

            // 1. Read only the target field first.
            SmallRyeConfig targetOnlyConfig =
                    new SmallRyeConfigBuilder()
                            .withSources(taskSource)
                            .withMapping(TaskTargetOnly.class)
                            .withValidateUnknown(false)
                            .build();

            TaskTargetOnly targetOnly = targetOnlyConfig.getConfigMapping(TaskTargetOnly.class);
            String targetId = targetOnly.target();

            // 2. Look up the target's type.
            String type = mainConfig.getValue("target." + targetId + ".type", String.class);
            if (type == null) {
                throw new ConfigurationException(
                        "Target type not found for target: " + targetId + " in file: " + taskFile);
            }

            // 3. Resolve the plugin.
            MigraphePlugin<?> plugin = pluginRegistry.getRequiredPlugin(type);

            // 4. Map onto the plugin's TaskDefinition class.
            // Note: recreate the YamlConfigSource (SmallRyeConfig consumes its sources).
            YamlConfigSource taskSource2 = new YamlConfigSource(taskFile.toUri().toURL());
            SmallRyeConfig taskConfig =
                    new SmallRyeConfigBuilder()
                            .withSources(taskSource2)
                            .withMapping(plugin.taskDefinitionClass())
                            .withValidateUnknown(false)
                            .build();

            return taskConfig.getConfigMapping(plugin.taskDefinitionClass());

        } catch (IOException e) {
            throw new ConfigurationException("Failed to load task file: " + taskFile, e);
        }
    }

    /**
     * Minimal {@code @ConfigMapping} used to read only a task file's {@code target} field, so the
     * full plugin-specific mapping can be selected before the file is mapped a second time.
     */
    @ConfigMapping(prefix = "")
    interface TaskTargetOnly {
        /**
         * The id of the target this task runs against.
         *
         * @return the target id
         */
        String target();
    }
}
