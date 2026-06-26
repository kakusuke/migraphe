package io.github.kakusuke.migraphe.core.config;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Derives task and target identifiers from file-system paths.
 *
 * <p>Migraphe assigns each task an auto-generated id based on its location under the {@code tasks/}
 * directory (see design decision 7 in the project documentation). This helper centralises that
 * convention: the id is the task file's path relative to {@code tasks/}, with the {@code .yaml}
 * extension stripped and Windows backslashes normalised to forward slashes. It is used by {@link
 * ConfigLoader} and {@link ConfigValidator} when building the {@link NodeId} for each task.
 */
public class TaskIdGenerator {

    /** Creates a new {@code TaskIdGenerator}. */
    public TaskIdGenerator() {}

    /**
     * Generates the {@link NodeId} (task id) for a task file.
     *
     * <p>The id is the file's path relative to {@code <baseDir>/tasks/}, without the {@code .yaml}
     * extension and with backslashes converted to forward slashes. Examples:
     *
     * <ul>
     *   <li>{@code /project/tasks/db1/create_users.yaml} &rarr; {@code "db1/create_users"}
     *   <li>{@code /project/tasks/db1/subfolder/add_index.yaml} &rarr; {@code
     *       "db1/subfolder/add_index"}
     * </ul>
     *
     * @param baseDir the project root directory (the one containing {@code tasks/})
     * @param taskFile the path to the task file
     * @return the generated task id
     * @throws IllegalArgumentException if {@code taskFile} does not reside under {@code
     *     baseDir/tasks/}
     * @throws NullPointerException if {@code baseDir} or {@code taskFile} is {@code null}
     */
    public NodeId generateTaskId(Path baseDir, Path taskFile) {
        Objects.requireNonNull(baseDir, "baseDir must not be null");
        Objects.requireNonNull(taskFile, "taskFile must not be null");

        // The tasks/ directory.
        Path tasksDir = baseDir.resolve("tasks");

        // Normalise taskFile to an absolute path.
        Path normalizedTaskFile = taskFile.toAbsolutePath().normalize();
        Path normalizedTasksDir = tasksDir.toAbsolutePath().normalize();

        // Ensure the file lives under tasks/.
        if (!normalizedTaskFile.startsWith(normalizedTasksDir)) {
            throw new IllegalArgumentException(
                    "Task file must be under tasks/ directory: " + taskFile);
        }

        // Compute the path relative to tasks/.
        Path relativePath = normalizedTasksDir.relativize(normalizedTaskFile);

        // Convert to a string and strip the .yaml extension.
        String pathStr = relativePath.toString();
        String idStr = pathStr.replaceAll("\\.yaml$", "");

        // Windows support: backslash -> forward slash.
        idStr = idStr.replace('\\', '/');

        return NodeId.of(idStr);
    }

    /**
     * Extracts the target id from a target file path.
     *
     * <p>The id is simply the file name with the {@code .yaml} extension removed. Examples:
     *
     * <ul>
     *   <li>{@code /project/targets/db1.yaml} &rarr; {@code "db1"}
     *   <li>{@code /project/targets/history.yaml} &rarr; {@code "history"}
     * </ul>
     *
     * @param targetsDir the path to the {@code targets/} directory (retained for symmetry with
     *     {@link #generateTaskId}; the id is derived solely from {@code targetFile}'s name)
     * @param targetFile the path to the target file
     * @return the target id (the file name without its {@code .yaml} extension)
     * @throws NullPointerException if {@code targetsDir} or {@code targetFile} is {@code null}
     */
    public String extractTargetId(Path targetsDir, Path targetFile) {
        Objects.requireNonNull(targetsDir, "targetsDir must not be null");
        Objects.requireNonNull(targetFile, "targetFile must not be null");

        // Get the file name.
        String fileName = targetFile.getFileName().toString();

        // Strip the .yaml extension.
        return fileName.replaceAll("\\.yaml$", "");
    }
}
