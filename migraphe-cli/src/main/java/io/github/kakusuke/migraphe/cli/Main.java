package io.github.kakusuke.migraphe.cli;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.cli.command.Command;
import io.github.kakusuke.migraphe.cli.command.DownCommand;
import io.github.kakusuke.migraphe.cli.command.StatusCommand;
import io.github.kakusuke.migraphe.cli.command.UpCommand;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
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
            Command command = createCommand(commandName, args, context);

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
    private static @Nullable Command createCommand(
            String commandName, String[] args, ExecutionContext context) {
        return switch (commandName) {
            case "up" -> new UpCommand(context);
            case "status" -> new StatusCommand(context);
            case "down" -> createDownCommand(args, context);
            default -> null;
        };
    }

    /** down コマンドを生成する。 */
    private static @Nullable Command createDownCommand(String[] args, ExecutionContext context) {
        List<String> argList = Arrays.asList(args);
        boolean skipConfirm = argList.contains("-y");
        boolean dryRun = argList.contains("--dry-run");

        // バージョン引数を取得（down, -y, --dry-run 以外の最初の引数）
        String version =
                argList.stream()
                        .filter(a -> !a.equals("down") && !a.equals("-y") && !a.equals("--dry-run"))
                        .findFirst()
                        .orElse(null);

        if (version == null) {
            System.err.println("Error: Version argument required for 'down' command");
            System.err.println("Usage: migraphe down [-y] [--dry-run] <version>");
            return null;
        }

        return new DownCommand(context, NodeId.of(version), skipConfirm, dryRun);
    }

    /** 使用方法を表示する。 */
    private static void printUsage() {
        System.out.println("Migraphe - Database Migration Tool");
        System.out.println();
        System.out.println("Usage: migraphe <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  up                          Execute pending migrations");
        System.out.println("  down [-y] [--dry-run] <v>   Rollback to version <v>");
        System.out.println("  status                      Show migration status");
        System.out.println();
        System.out.println("Down options:");
        System.out.println("  -y          Skip confirmation prompt");
        System.out.println("  --dry-run   Show plan without executing");
        System.out.println();
    }
}
