package io.github.migraphe.cli.config;

import io.github.migraphe.core.graph.NodeId;
import java.nio.file.Path;
import java.util.Objects;

/** ファイルパスから Task ID を生成するジェネレーター。 */
public class TaskIdGenerator {

    /**
     * タスクファイルのパスから Task ID (NodeId) を生成する。
     *
     * <p>例:
     *
     * <ul>
     *   <li>/project/tasks/db1/create_users.yaml → "db1/create_users"
     *   <li>/project/tasks/db1/subfolder/add_index.yaml → "db1/subfolder/add_index"
     * </ul>
     *
     * @param baseDir プロジェクトルート
     * @param taskFile タスクファイルのパス
     * @return Task ID (NodeId)
     * @throws IllegalArgumentException タスクファイルが tasks/ 配下にない場合
     */
    public NodeId generateTaskId(Path baseDir, Path taskFile) {
        Objects.requireNonNull(baseDir, "baseDir must not be null");
        Objects.requireNonNull(taskFile, "taskFile must not be null");

        // tasks/ ディレクトリ
        Path tasksDir = baseDir.resolve("tasks");

        // taskFile を絶対パスに正規化
        Path normalizedTaskFile = taskFile.toAbsolutePath().normalize();
        Path normalizedTasksDir = tasksDir.toAbsolutePath().normalize();

        // tasks/ 配下にあるかチェック
        if (!normalizedTaskFile.startsWith(normalizedTasksDir)) {
            throw new IllegalArgumentException(
                    "Task file must be under tasks/ directory: " + taskFile);
        }

        // tasks/ からの相対パスを取得
        Path relativePath = normalizedTasksDir.relativize(normalizedTaskFile);

        // パス文字列に変換し、拡張子 .yaml を削除
        String pathStr = relativePath.toString();
        String idStr = pathStr.replaceAll("\\.yaml$", "");

        // Windows 対応: バックスラッシュ → スラッシュ
        idStr = idStr.replace('\\', '/');

        return NodeId.of(idStr);
    }

    /**
     * ターゲットファイルのパスから Target ID を抽出する。
     *
     * <p>例:
     *
     * <ul>
     *   <li>/project/targets/db1.yaml → "db1"
     *   <li>/project/targets/history.yaml → "history"
     * </ul>
     *
     * @param targetsDir targets/ ディレクトリのパス
     * @param targetFile ターゲットファイルのパス
     * @return Target ID (ファイル名から拡張子を除いたもの)
     */
    public String extractTargetId(Path targetsDir, Path targetFile) {
        Objects.requireNonNull(targetsDir, "targetsDir must not be null");
        Objects.requireNonNull(targetFile, "targetFile must not be null");

        // ファイル名を取得
        String fileName = targetFile.getFileName().toString();

        // 拡張子 .yaml を削除
        return fileName.replaceAll("\\.yaml$", "");
    }
}
