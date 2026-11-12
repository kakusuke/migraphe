package io.github.kakusuke.migraphe.core.config;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jspecify.annotations.Nullable;

/**
 * Validates a Migraphe configuration offline, without any database connection.
 *
 * <p>Backs the {@code migraphe validate} command. Rather than failing on the first problem, it
 * accumulates every detected error and returns them together so the user can fix them in one pass.
 * The checks cover: presence of {@code migraphe.yaml}; required and well-typed fields in each
 * {@code targets/*.yaml} and <code>tasks/&#42;&#42;/*.yaml</code> (including plugin-specific
 * mapping validation via {@link PluginRegistry}); resolvability of task {@code dependencies}; and
 * absence of circular dependencies.
 */
public class ConfigValidator {

    private final PluginRegistry pluginRegistry;

    /**
     * Creates a validator backed by the given plugin registry.
     *
     * @param pluginRegistry the registry used to resolve target types and plugin-specific task
     *     mappings
     */
    public ConfigValidator(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    /**
     * The outcome of a validation run.
     *
     * @param errors the accumulated error messages, empty when the configuration is valid
     */
    public record ValidationOutput(List<String> errors) {
        /**
         * Indicates whether validation passed.
         *
         * @return {@code true} if no errors were collected
         */
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    /**
     * Validates the configuration rooted at the given directory.
     *
     * <p>Resolves the scan root from {@code project.scan-root}, then validates targets, tasks, task
     * dependencies and dependency cycles, accumulating all problems. If {@code migraphe.yaml} is
     * missing, that single error is returned immediately since no further check is meaningful.
     *
     * @param baseDir the project base directory
     * @return the accumulated validation result
     */
    public ValidationOutput validate(Path baseDir) {
        return validate(baseDir, null);
    }

    /**
     * Validates the configuration rooted at the given directory, applying an environment overlay.
     *
     * <p>Behaves like {@link #validate(Path)} but, when {@code envName} is given, resolves each
     * target's effective {@code type} through {@code environments/<envName>.yaml}, so the
     * configuration that would actually be used at run time is the one validated. A requested
     * overlay that does not exist is reported as a validation error rather than thrown, because
     * {@code validate} accumulates problems instead of aborting.
     *
     * @param baseDir the project base directory
     * @param envName the deployment-environment name whose overlay is applied, or {@code null} to
     *     validate the base configuration only
     * @return the accumulated validation result
     */
    public ValidationOutput validate(Path baseDir, @Nullable String envName) {
        List<String> errors = new ArrayList<>();
        YamlFileScanner scanner = new YamlFileScanner();
        TaskIdGenerator idGenerator = new TaskIdGenerator();

        // 1. Ensure migraphe.yaml exists.
        Path projectConfigFile = scanner.findProjectConfig(baseDir);
        if (projectConfigFile == null) {
            errors.add("migraphe.yaml: Project configuration file not found");
            // Without migraphe.yaml no further validation is meaningful.
            return new ValidationOutput(errors);
        }

        // Resolve the scan root: baseDir/scan-root if project.scan-root is set, otherwise baseDir.
        Path scanRoot = new ConfigLoader().resolveScanRoot(baseDir);

        // Resolve the environment overlay when one was requested, so the effective (run-time)
        // configuration is what gets validated.
        Map<String, String> overlay = new HashMap<>();
        if (envName != null) {
            errors.addAll(loadOverlay(scanner, scanRoot, envName, overlay));
        }

        // 2. Load and validate each targets/*.yaml individually.
        List<Path> targetFiles = scanner.scanTargetFiles(scanRoot);
        List<Path> readableTargetFiles = new ArrayList<>();
        Set<String> validTargetIds = new HashSet<>();
        Map<String, String> targetTypes = new HashMap<>();

        for (Path targetFile : targetFiles) {
            String targetId = targetIdOf(targetFile);
            List<String> readErrors = readYamlFile(targetFile, "targets/" + targetId + ".yaml");
            if (readErrors.isEmpty()) {
                readableTargetFiles.add(targetFile);
            } else {
                errors.addAll(readErrors);
            }
        }

        List<Path> taskFiles = scanner.scanTaskFiles(scanRoot);
        Map<NodeId, Path> readableTaskFiles = new HashMap<>();

        for (Path taskFile : taskFiles) {
            NodeId taskId = idGenerator.generateTaskId(scanRoot, taskFile);
            List<String> readErrors = readYamlFile(taskFile, "tasks/" + taskId.value() + ".yaml");
            if (readErrors.isEmpty()) {
                readableTaskFiles.put(taskId, taskFile);
            } else {
                errors.addAll(readErrors);
            }
        }

        SmallRyeConfig resolved =
                ConfigLoader.buildConfig(
                        new MultiFileYamlConfigSource(
                                projectConfigFile, readableTargetFiles, readableTaskFiles),
                        overlay.isEmpty() ? null : new MapConfigSource(overlay, 500),
                        Map.of());

        for (Path targetFile : readableTargetFiles) {
            String targetId = targetIdOf(targetFile);
            ConfigValue typeValue = resolved.getConfigValue("target." + targetId + ".type");
            String type = typeValue.getValue();
            if (type == null && typeValue.getRawValue() != null) {
                errors.add(
                        "targets/"
                                + targetId
                                + ".yaml: Could not expand 'type': "
                                + typeValue.getRawValue());
                continue;
            }
            List<String> targetErrors = validateTargetType(targetId, type);
            if (targetErrors.isEmpty() && type != null) {
                validTargetIds.add(targetId);
                // Capture the effective target type for later plugin-specific task validation.
                targetTypes.put(targetId, type);
            } else {
                errors.addAll(targetErrors);
            }
        }

        // 3. Validate each task as resolved.
        Map<NodeId, TaskInfo> validTasks = new HashMap<>();

        Set<NodeId> taskIds;
        try {
            taskIds = ConfigLoader.extractTaskIds(resolved);
        } catch (ConfigurationException e) {
            errors.add(e.getMessage());
            taskIds = Set.of();
        }

        Set<NodeId> disabledTasks = new HashSet<>();
        Set<NodeId> conditionalTasks = new HashSet<>();
        for (NodeId taskId : taskIds) {
            Boolean enabled = null;
            try {
                enabled = ConfigLoader.declaredEnabled(resolved, taskId);
            } catch (ConfigurationException e) {
                errors.add("tasks/" + taskId.value() + ".yaml: " + e.getMessage());
            }
            if (enabled != null) {
                conditionalTasks.add(taskId);
            }
            if (Boolean.FALSE.equals(enabled)) {
                disabledTasks.add(taskId);
                continue;
            }
            PrefixedConfigSource task;
            try {
                task =
                        new PrefixedConfigSource(
                                resolved, MultiFileYamlConfigSource.taskKeyPrefix(taskId));
            } catch (NoSuchElementException e) {
                errors.add("tasks/" + taskId.value() + ".yaml: " + e.getMessage());
                continue;
            }
            List<String> taskErrors = validateTask(task, taskId, validTargetIds, targetTypes);
            if (taskErrors.isEmpty()) {
                validTasks.put(taskId, extractTaskInfo(task));
            } else {
                errors.addAll(taskErrors);
            }
        }

        // 4. Verify every dependency reference points to a known task.
        for (Map.Entry<NodeId, TaskInfo> entry : validTasks.entrySet()) {
            NodeId taskId = entry.getKey();
            TaskInfo taskInfo = entry.getValue();

            for (String depId : taskInfo.dependencies()) {
                NodeId depNodeId = NodeId.of(depId);
                if (disabledTasks.contains(depNodeId)) {
                    errors.add(
                            "tasks/"
                                    + taskId.value()
                                    + ".yaml: Dependency '"
                                    + depId
                                    + "' is disabled (enabled: is false)");
                } else if (conditionalTasks.contains(depNodeId)
                        && !conditionalTasks.contains(taskId)) {
                    errors.add(
                            "tasks/"
                                    + taskId.value()
                                    + ".yaml: Dependency '"
                                    + depId
                                    + "' declares enabled: and this task does not; declare"
                                    + " enabled: here too, or drop the dependency");
                } else if (!validTasks.containsKey(depNodeId)) {
                    errors.add(
                            "tasks/"
                                    + taskId.value()
                                    + ".yaml: Dependency '"
                                    + depId
                                    + "' not found");
                }
            }
        }

        // 5. Detect dependency cycles (DFS).
        List<String> cycleErrors = detectCycles(validTasks);
        errors.addAll(cycleErrors);

        return new ValidationOutput(errors);
    }

    /** Internal holder for the fields of a task needed during validation. */
    private record TaskInfo(String target, List<String> dependencies) {}

    /**
     * Reads {@code environments/<envName>.yaml} into {@code overlay}, returning any problems found.
     *
     * <p>A missing or unreadable overlay yields a validation error rather than an exception, so the
     * rest of the configuration is still checked and every problem is reported in one run.
     *
     * @param scanner the scanner used to locate and list overlays
     * @param scanRoot the scan-root directory (the one containing {@code environments/})
     * @param envName the requested environment name
     * @param overlay the map the overlay properties are read into
     * @return the errors encountered, or an empty list when the overlay loaded cleanly
     */
    private List<String> loadOverlay(
            YamlFileScanner scanner, Path scanRoot, String envName, Map<String, String> overlay) {
        String relativePath = "environments/" + envName + ".yaml";
        Path envFile = scanner.findEnvironmentFile(scanRoot, envName);

        if (envFile == null) {
            List<String> available = scanner.listEnvironmentNames(scanRoot);
            String availableText = available.isEmpty() ? "(none)" : String.join(", ", available);
            return List.of(
                    relativePath
                            + ": Environment overlay not found for --env "
                            + envName
                            + " (available: "
                            + availableText
                            + ")");
        }

        try {
            overlay.putAll(MultiFileYamlConfigSource.rawAwareYamlSource(envFile).getProperties());
            return List.of();
        } catch (IOException e) {
            return List.of(relativePath + ": Failed to load - " + e.getMessage());
        } catch (Exception e) {
            return List.of(relativePath + ": Invalid YAML - " + e.getMessage());
        }
    }

    /** Derives a target's id from its file name ({@code db1.yaml} &rarr; {@code db1}). */
    private static String targetIdOf(Path targetFile) {
        return targetFile.getFileName().toString().replaceAll("\\.yaml$", "");
    }

    /** Reads a single YAML file, reporting it when it cannot be loaded or parsed. */
    private List<String> readYamlFile(Path file, String relativePath) {
        try {
            MultiFileYamlConfigSource.rawAwareYamlSource(file);
            return List.of();
        } catch (IOException e) {
            return List.of(relativePath + ": Failed to load - " + e.getMessage());
        } catch (Exception e) {
            return List.of(relativePath + ": Invalid YAML - " + e.getMessage());
        }
    }

    /** Validates a target's resolved {@code type}. */
    private List<String> validateTargetType(String targetId, @Nullable String type) {
        String relativePath = "targets/" + targetId + ".yaml";
        if (type == null) {
            return List.of(relativePath + ": Missing required property 'type'");
        }
        if (!pluginRegistry.hasPlugin(type)) {
            return List.of(relativePath + ": Unknown plugin type '" + type + "'");
        }
        return List.of();
    }

    /** Validates a single task's resolved keys. */
    private List<String> validateTask(
            ConfigSource task,
            NodeId taskId,
            Set<String> validTargetIds,
            Map<String, String> targetTypes) {
        List<String> errors = new ArrayList<>();
        String relativePath = "tasks/" + taskId.value() + ".yaml";
        Map<String, String> props = task.getProperties();

        // 'name' is required.
        if (props.get("name") == null) {
            errors.add(relativePath + ": Missing required property 'name'");
        }

        // 'target' is required.
        String target = props.get("target");
        if (target == null) {
            errors.add(relativePath + ": Missing required property 'target'");
        } else if (!validTargetIds.contains(target)) {
            errors.add(relativePath + ": Target '" + target + "' not found");
        } else {
            // Run plugin-specific validation using the target's type.
            String type = targetTypes.get(target);
            if (type != null) {
                errors.addAll(validateWithPlugin(task, type, relativePath));
            }
        }

        return errors;
    }

    /**
     * Runs plugin-specific validation by mapping the task file onto the plugin's task definition.
     */
    private List<String> validateWithPlugin(ConfigSource task, String type, String relativePath) {
        List<String> errors = new ArrayList<>();

        try {
            var plugin = pluginRegistry.getPlugin(type);
            if (plugin == null) {
                return errors;
            }

            Class<?> taskDefClass = plugin.taskDefinitionClass();

            // Attempt the SmallRye Config mapping.
            SmallRyeConfig config =
                    new SmallRyeConfigBuilder()
                            .withSources(task)
                            .withMapping(taskDefClass)
                            .withValidateUnknown(false)
                            .build();

            // Resolve the mapping; an exception here means a required field is missing.
            Object mapped = config.getConfigMapping(taskDefClass);

            if (mapped instanceof TaskDefinition<?> definition
                    && definition.down().isEmpty()
                    && definition.noWayBack().isEmpty()) {
                errors.add(
                        relativePath
                                + ": defines neither down: nor no_way_back:. Write the rollback, or"
                                + " state why there is none so the decision is on record.");
            }

        } catch (io.smallrye.config.ConfigValidationException e) {
            // Extract messages from the SmallRye validation error.
            for (String problem : extractValidationProblems(e)) {
                errors.add(relativePath + ": " + problem);
            }
        } catch (Exception e) {
            // Other errors (mapping failures, etc.).
            String message = e.getMessage();
            if (message != null && message.contains("required")) {
                errors.add(relativePath + ": " + message);
            }
        }

        return errors;
    }

    /** Extracts human-readable error messages from a SmallRye validation exception. */
    private List<String> extractValidationProblems(
            io.smallrye.config.ConfigValidationException exception) {
        List<String> problems = new ArrayList<>();
        String message = exception.getMessage();

        if (message != null) {
            // Recognise messages of the form "property name is required".
            if (message.contains("is required")) {
                problems.add("Missing required property - " + message);
            } else {
                problems.add(message);
            }
        }

        return problems;
    }

    /** Extracts a {@link TaskInfo} (target and dependencies) from a task's resolved keys. */
    private TaskInfo extractTaskInfo(ConfigSource task) {
        Map<String, String> props = task.getProperties();

        String target = props.getOrDefault("target", "");
        List<String> dependencies = new ArrayList<>();

        // Extract dependencies (list-indexed keys).
        for (Map.Entry<String, String> entry : props.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("dependencies[") && key.endsWith("]")) {
                dependencies.add(entry.getValue());
            }
        }

        return new TaskInfo(target, dependencies);
    }

    /** Detects circular task dependencies using depth-first search. */
    private List<String> detectCycles(Map<NodeId, TaskInfo> tasks) {
        List<String> errors = new ArrayList<>();
        Set<NodeId> visited = new HashSet<>();
        Set<NodeId> recursionStack = new HashSet<>();
        List<NodeId> path = new ArrayList<>();

        for (NodeId nodeId : tasks.keySet()) {
            if (!visited.contains(nodeId)) {
                String cycleError = detectCyclesDFS(nodeId, tasks, visited, recursionStack, path);
                if (cycleError != null) {
                    errors.add(cycleError);
                    break; // Report only the first cycle found.
                }
            }
        }

        return errors;
    }

    /** Recursive DFS helper that reports the first cycle reachable from {@code nodeId}. */
    private @Nullable String detectCyclesDFS(
            NodeId nodeId,
            Map<NodeId, TaskInfo> tasks,
            Set<NodeId> visited,
            Set<NodeId> recursionStack,
            List<NodeId> path) {

        visited.add(nodeId);
        recursionStack.add(nodeId);
        path.add(nodeId);

        TaskInfo taskInfo = tasks.get(nodeId);
        if (taskInfo != null) {
            for (String depIdStr : taskInfo.dependencies()) {
                NodeId depId = NodeId.of(depIdStr);

                if (!tasks.containsKey(depId)) {
                    // Missing dependency (already reported as a separate error).
                    continue;
                }

                if (recursionStack.contains(depId)) {
                    // Cycle detected.
                    int cycleStart = path.indexOf(depId);
                    List<NodeId> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                    cycle.add(depId);
                    return "Circular dependency detected: "
                            + cycle.stream()
                                    .map(NodeId::value)
                                    .reduce((a, b) -> a + " -> " + b)
                                    .orElse("");
                }

                if (!visited.contains(depId)) {
                    String cycleError =
                            detectCyclesDFS(depId, tasks, visited, recursionStack, path);
                    if (cycleError != null) {
                        return cycleError;
                    }
                }
            }
        }

        recursionStack.remove(nodeId);
        path.remove(path.size() - 1);
        return null;
    }
}
