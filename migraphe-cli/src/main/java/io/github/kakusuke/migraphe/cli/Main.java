package io.github.kakusuke.migraphe.cli;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.cli.command.Command;
import io.github.kakusuke.migraphe.cli.command.DownCommand;
import io.github.kakusuke.migraphe.cli.command.GenerateCommand;
import io.github.kakusuke.migraphe.cli.command.PluginPinCommand;
import io.github.kakusuke.migraphe.cli.command.StatusCommand;
import io.github.kakusuke.migraphe.cli.command.UpCommand;
import io.github.kakusuke.migraphe.cli.command.ValidateCommand;
import io.github.kakusuke.migraphe.cli.resolver.MavenPluginResolver;
import io.github.kakusuke.migraphe.cli.resolver.PluginConfigParseResult;
import io.github.kakusuke.migraphe.cli.resolver.PluginConfigPreParser;
import io.github.kakusuke.migraphe.cli.resolver.PluginResolutionException;
import io.github.kakusuke.migraphe.cli.resolver.PluginResolver;
import io.github.kakusuke.migraphe.cli.resolver.RepositoryConfig;
import io.github.kakusuke.migraphe.cli.resolver.RepositoryRegistry;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Migraphe CLI のエントリーポイント。 */
public class Main {

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {
        try {
            if (args.length == 0) {
                printUsage();
                return 1;
            }

            String commandName = args[0];

            if ("-v".equals(commandName) || "--version".equals(commandName)) {
                System.out.println(VersionInfo.load(Main.class.getClassLoader()).format());
                return 0;
            }

            // プロジェクトディレクトリの決定（カレントディレクトリ）
            Path baseDir = Paths.get(System.getProperty("user.dir"));

            // pin コマンドはプラグイン解決前に実行（lock 生成用）
            if ("pin".equals(commandName)) {
                boolean checkOnly = Arrays.asList(args).contains("--check");
                return new PluginPinCommand(baseDir, defaultMavenResolver(baseDir), checkOnly)
                        .execute();
            }

            // Maven Resolver でプラグイン依存を解決
            PluginConfigParseResult parsed =
                    new PluginConfigPreParser().parse(baseDir.resolve("migraphe.yaml"));
            PluginResolver pluginResolver = new PluginResolver();
            URLClassLoader pluginClassLoader = pluginResolver.resolve(baseDir);

            // PluginRegistry を初期化
            PluginRegistry pluginRegistry =
                    initializePluginRegistry(baseDir, parsed, pluginClassLoader);

            // validate コマンドは ExecutionContext を必要としない（オフライン検証）
            if ("validate".equals(commandName)) {
                ValidateCommand validateCommand = new ValidateCommand(baseDir, pluginRegistry);
                return validateCommand.execute();
            }

            // generate コマンドは独自に設定をロードする
            if ("generate".equals(commandName)) {
                String nameFilter = parseNameOption(args);
                Path pluginsDir = resolvePluginsDir(baseDir, parsed);
                GenerateCommand generateCommand =
                        new GenerateCommand(
                                baseDir, pluginRegistry, pluginClassLoader, nameFilter, pluginsDir);
                return generateCommand.execute();
            }

            // ExecutionContext をロード（--env オプションを考慮）
            ExecutionContext context = loadContext(baseDir, pluginRegistry, args);

            Command command = createCommand(commandName, args, context);

            if (command == null) {
                System.err.println("Unknown command: " + commandName);
                printUsage();
                return 1;
            }

            return command.execute();

        } catch (Exception e) {
            return handleException(e);
        }
    }

    /** PluginRegistry を初期化する。 */
    private static PluginRegistry initializePluginRegistry(
            Path baseDir,
            PluginConfigParseResult parsed,
            @Nullable URLClassLoader pluginClassLoader) {
        PluginRegistry registry = new PluginRegistry();

        // 1. クラスパスからプラグインをロード
        registry.loadFromClasspath();

        // 2. Maven Resolver で解決したプラグインをロード
        if (pluginClassLoader != null) {
            registry.loadFromClassLoader(pluginClassLoader);
        }

        // 3. plugins/ ディレクトリからプラグインをロード（後方互換）
        Path pluginsDir = resolvePluginsDir(baseDir, parsed);
        registry.loadFromDirectory(pluginsDir);

        return registry;
    }

    /** コマンド名から Command インスタンスを生成する。 */
    private static @Nullable Command createCommand(
            String commandName, String[] args, ExecutionContext context) {
        return switch (commandName) {
            case "up" -> createUpCommand(args, context);
            case "status" -> new StatusCommand(context);
            case "down" -> createDownCommand(args, context);
            default -> null;
        };
    }

    /** up コマンドを生成する。 */
    private static Command createUpCommand(String[] args, ExecutionContext context) {
        List<String> argList = Arrays.asList(args);
        boolean skipConfirm = argList.contains("-y");
        boolean dryRun = argList.contains("--dry-run");

        String targetId = firstPositionalArg(args);

        NodeId nodeId = targetId != null ? NodeId.of(targetId) : null;
        return new UpCommand(context, nodeId, skipConfirm, dryRun);
    }

    /** down コマンドを生成する。 */
    private static @Nullable Command createDownCommand(String[] args, ExecutionContext context) {
        List<String> argList = Arrays.asList(args);
        boolean skipConfirm = argList.contains("-y");
        boolean dryRun = argList.contains("--dry-run");
        boolean allMigrations = argList.contains("--all");

        String version = firstPositionalArg(args);

        // --all が指定されていない場合はバージョンが必要
        if (!allMigrations && version == null) {
            System.err.println("Error: Version argument or --all required for 'down' command");
            System.err.println("Usage: migraphe down [-y] [--dry-run] [--all | <version>]");
            return null;
        }

        NodeId targetVersion = version != null ? NodeId.of(version) : null;
        return new DownCommand(context, targetVersion, allMigrations, skipConfirm, dryRun);
    }

    /** --name オプションの値を取得する。 */
    private static @Nullable String parseNameOption(String[] args) {
        return parseValueOption(args, "--name");
    }

    /** --env オプションの値を取得する。 */
    static @Nullable String parseEnvOption(String[] args) {
        return parseValueOption(args, "--env");
    }

    /** args から --env オプションを解析して ExecutionContext をロードする。 */
    static ExecutionContext loadContext(
            Path baseDir, PluginRegistry pluginRegistry, String[] args) {
        String envName = parseEnvOption(args);
        return ExecutionContext.load(baseDir, pluginRegistry, envName);
    }

    /** 先頭コマンド語・値付きフラグ・真偽フラグを除いた最初の位置引数を返す。存在しない場合は null。 */
    static @Nullable String firstPositionalArg(String[] args) {
        if (args.length == 0) {
            return null;
        }
        // 値を取るフラグ（フラグ自身 + 次トークンをスキップ）
        Set<String> valueFlags = Set.of("--env", "--name");
        // 真偽フラグ（単体でスキップ）
        Set<String> boolFlags = Set.of("-y", "--dry-run", "--all");
        int i = 1; // args[0] はコマンド語なので飛ばす
        while (i < args.length) {
            String a = args[i];
            if (valueFlags.contains(a)) {
                i += 2; // フラグと値をスキップ
            } else if (boolFlags.contains(a)) {
                i += 1;
            } else {
                return a;
            }
        }
        return null;
    }

    /** 指定したフラグの次引数を取得する。フラグが存在しない場合は null を返す。 */
    static @Nullable String parseValueOption(String[] args, String flag) {
        List<String> argList = Arrays.asList(args);
        for (int i = 0; i < argList.size() - 1; i++) {
            if (flag.equals(argList.get(i))) {
                return argList.get(i + 1);
            }
        }
        return null;
    }

    static Path resolvePluginsDir(Path baseDir, PluginConfigParseResult parsed) {
        Path scanRoot = parsed.scanRoot().map(baseDir::resolve).orElse(baseDir);
        return scanRoot.resolve("plugins");
    }

    static boolean shouldPrintStackTrace(Exception e) {
        return !(e instanceof IllegalArgumentException || e instanceof PluginResolutionException);
    }

    private static MavenPluginResolver defaultMavenResolver(Path baseDir) {
        PluginConfigParseResult parsed =
                new PluginConfigPreParser().parse(baseDir.resolve("migraphe.yaml"));
        List<RepositoryConfig> all = new ArrayList<>();
        all.add(RepositoryConfig.mavenCentral());
        for (RepositoryConfig r : parsed.repositories()) {
            if (!"maven-central".equals(r.id())) {
                all.add(r);
            }
        }
        Path localRepo = defaultLocalRepo();
        return new MavenPluginResolver(localRepo, RepositoryRegistry.of(all));
    }

    private static Path defaultLocalRepo() {
        String m2Home = System.getProperty("maven.repo.local");
        if (m2Home != null) {
            return Path.of(m2Home);
        }
        return Path.of(System.getProperty("user.home"), ".m2", "repository");
    }

    static int handleException(Exception e) {
        System.err.println("Error: " + e.getMessage());
        if (shouldPrintStackTrace(e)) {
            e.printStackTrace();
        }
        return 1;
    }

    /** 使用方法を表示する。 */
    private static void printUsage() {
        System.out.println("Migraphe - Database Migration Tool");
        System.out.println();
        System.out.println("Usage: migraphe <command> [options]");
        System.out.println("       migraphe -v | --version");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  up [-y] [--dry-run] [<id>]          Execute migrations");
        System.out.println("  down [-y] [--dry-run] [--all | <v>] Rollback migrations");
        System.out.println("  status                              Show migration status");
        System.out.println(
                "  validate                            Validate configuration (offline)");
        System.out.println("  generate [--name <name>]            Run generators");
        System.out.println(
                "  pin [--check]                       Generate or verify migraphe.lock.yaml");
        System.out.println();
        System.out.println("Up options:");
        System.out.println("  <id>           Execute migrations up to and including <id>");
        System.out.println("  --env <name>   Use the specified environment overlay");
        System.out.println("  -y             Skip confirmation prompt");
        System.out.println("  --dry-run      Show plan without executing");
        System.out.println();
        System.out.println("Down options:");
        System.out.println("  <version>      Rollback migrations that depend on <version>");
        System.out.println("  --all          Rollback all executed migrations");
        System.out.println("  --env <name>   Use the specified environment overlay");
        System.out.println("  -y             Skip confirmation prompt");
        System.out.println("  --dry-run      Show plan without executing");
        System.out.println();
        System.out.println("Generate options:");
        System.out.println("  --name <name>  Run only the generator with matching name");
        System.out.println();
    }
}
