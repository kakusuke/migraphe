package io.github.kakusuke.migraphe.cli.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** YAMLファイルをディレクトリから発見するスキャナー。 */
public class YamlFileScanner {

    /**
     * プロジェクト設定ファイル (migraphe.yaml) を検索する。
     *
     * @param baseDir プロジェクトルート
     * @return migraphe.yaml のパス (存在しない場合は null)
     */
    public @Nullable Path findProjectConfig(Path baseDir) {
        Path projectConfig = baseDir.resolve("migraphe.yaml");
        return Files.exists(projectConfig) ? projectConfig : null;
    }

    /**
     * targets/ ディレクトリ配下の全 .yaml ファイルを収集する。
     *
     * @param baseDir プロジェクトルート
     * @return targets/*.yaml のリスト (ディレクトリが存在しない場合は空リスト)
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
     * tasks/ ディレクトリ配下の全 .yaml ファイルを再帰的に収集する。
     *
     * @param baseDir プロジェクトルート
     * @return tasks/**\/*.yaml のリスト (ディレクトリが存在しない場合は空リスト)
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
     * environments/{envName}.yaml を検索する。
     *
     * @param baseDir プロジェクトルート
     * @param envName 環境名 (例: "development", "production")
     * @return environments/{envName}.yaml のパス (存在しない場合は null)
     */
    public @Nullable Path findEnvironmentFile(Path baseDir, String envName) {
        Path environmentsDir = baseDir.resolve("environments");
        Path envFile = environmentsDir.resolve(envName + ".yaml");

        return Files.exists(envFile) ? envFile : null;
    }
}
