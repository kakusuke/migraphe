package io.github.migraphe.cli;

import io.github.migraphe.cli.command.Command;
import io.github.migraphe.cli.command.StatusCommand;
import io.github.migraphe.cli.command.UpCommand;
import io.github.migraphe.core.plugin.PluginRegistry;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jspecify.annotations.Nullable;

/** Migraphe CLI のエントリーポイント。 */
public class Main {

    public static void main(String[] args) {
        try {
            // 引数チェック
            if (args.length == 0) {
                printUsage();
                System.exit(1);
            }

            String commandName = args[0];

            // プロジェクトディレクトリの決定（カレントディレクトリ）
            Path baseDir = Paths.get(System.getProperty("user.dir"));

            // PluginRegistry を初期化
            PluginRegistry pluginRegistry = initializePluginRegistry(baseDir);

            // ExecutionContext をロード
            ExecutionContext context = ExecutionContext.load(baseDir, pluginRegistry);

            // コマンドを実行
            Command command = createCommand(commandName, context);

            if (command == null) {
                System.err.println("Unknown command: " + commandName);
                printUsage();
                System.exit(1);
                return; // Unreachable, but helps NullAway understand flow
            }

            int exitCode = command.execute();
            System.exit(exitCode);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /** PluginRegistry を初期化する。 */
    private static PluginRegistry initializePluginRegistry(Path baseDir) {
        PluginRegistry registry = new PluginRegistry();

        // 1. クラスパスからプラグインをロード
        registry.loadFromClasspath();

        // 2. plugins/ ディレクトリからプラグインをロード
        Path pluginsDir = baseDir.resolve("plugins");
        registry.loadFromDirectory(pluginsDir);

        return registry;
    }

    /** コマンド名から Command インスタンスを生成する。 */
    private static @Nullable Command createCommand(String commandName, ExecutionContext context) {
        return switch (commandName) {
            case "up" -> new UpCommand(context);
            case "status" -> new StatusCommand(context);
            default -> null;
        };
    }

    /** 使用方法を表示する。 */
    private static void printUsage() {
        System.out.println("Migraphe - Database Migration Tool");
        System.out.println();
        System.out.println("Usage: migraphe <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  up      Execute pending migrations");
        System.out.println("  status  Show migration status");
        System.out.println();
    }
}
