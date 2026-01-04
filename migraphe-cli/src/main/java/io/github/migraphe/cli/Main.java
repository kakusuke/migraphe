package io.github.migraphe.cli;

import io.github.migraphe.cli.command.Command;
import io.github.migraphe.cli.command.StatusCommand;
import io.github.migraphe.cli.command.UpCommand;
import java.nio.file.Path;
import java.nio.file.Paths;

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

            // ExecutionContext をロード
            ExecutionContext context = ExecutionContext.load(baseDir);

            // コマンドを実行
            Command command = createCommand(commandName, context);

            if (command == null) {
                System.err.println("Unknown command: " + commandName);
                printUsage();
                System.exit(1);
            }

            int exitCode = command.execute();
            System.exit(exitCode);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /** コマンド名から Command インスタンスを生成する。 */
    private static Command createCommand(String commandName, ExecutionContext context) {
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
