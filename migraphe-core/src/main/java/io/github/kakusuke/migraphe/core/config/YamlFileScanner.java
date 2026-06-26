package io.github.kakusuke.migraphe.core.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Locates the YAML files that make up a Migraphe configuration on disk.
 *
 * <p>Migraphe spreads its configuration across several conventionally-named files and directories
 * ({@code migraphe.yaml}, {@code targets/*.yaml}, <code>tasks/&#42;&#42;/*.yaml</code>, {@code
 * environments/*.yaml}). This scanner encapsulates the file-system discovery for those locations so
 * that {@link ConfigLoader} and {@link ConfigValidator} can stay focused on interpreting the
 * resulting files. All methods are pure look-ups and never modify the file system.
 */
public class YamlFileScanner {

    /** Creates a new {@code YamlFileScanner}. */
    public YamlFileScanner() {}

    /**
     * Locates the project configuration file ({@code migraphe.yaml}).
     *
     * @param baseDir the directory to look in (the project root)
     * @return the path to {@code migraphe.yaml}, or {@code null} if it does not exist
     */
    public @Nullable Path findProjectConfig(Path baseDir) {
        Path projectConfig = baseDir.resolve("migraphe.yaml");
        return Files.exists(projectConfig) ? projectConfig : null;
    }

    /**
     * Collects all {@code .yaml} files directly under the {@code targets/} directory.
     *
     * <p>The scan is non-recursive (target ids are flat). The returned order is unspecified.
     *
     * @param baseDir the scan-root directory (the one containing {@code targets/})
     * @return the list of {@code targets/*.yaml} files, or an empty list if the directory is absent
     * @throws java.io.UncheckedIOException if the directory cannot be listed
     */
    public List<Path> scanTargetFiles(Path baseDir) {
        Path targetsDir = baseDir.resolve("targets");

        if (!Files.exists(targetsDir) || !Files.isDirectory(targetsDir)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.list(targetsDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".yaml"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan target files in " + targetsDir, e);
        }
    }

    /**
     * Recursively collects all {@code .yaml} files under the {@code tasks/} directory.
     *
     * <p>Unlike {@link #scanTargetFiles}, this walk descends into sub-directories, since task ids
     * may be nested (for example {@code db1/create_users}). The returned order is unspecified.
     *
     * @param baseDir the scan-root directory (the one containing {@code tasks/})
     * @return the list of <code>tasks/&#42;&#42;/*.yaml</code> files, or an empty list if the
     *     directory is absent
     * @throws java.io.UncheckedIOException if the directory tree cannot be walked
     */
    public List<Path> scanTaskFiles(Path baseDir) {
        Path tasksDir = baseDir.resolve("tasks");

        if (!Files.exists(tasksDir) || !Files.isDirectory(tasksDir)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(tasksDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".yaml"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan task files in " + tasksDir, e);
        }
    }

    /**
     * Locates the environment-override file {@code environments/<envName>.yaml}.
     *
     * @param baseDir the scan-root directory (the one containing {@code environments/})
     * @param envName the deployment-environment name (for example {@code "development"} or {@code
     *     "production"})
     * @return the path to {@code environments/<envName>.yaml}, or {@code null} if it does not exist
     */
    public @Nullable Path findEnvironmentFile(Path baseDir, String envName) {
        Path environmentsDir = baseDir.resolve("environments");
        Path envFile = environmentsDir.resolve(envName + ".yaml");

        return Files.exists(envFile) ? envFile : null;
    }
}
