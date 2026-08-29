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

/**
 * Entry point for the Migraphe command-line interface.
 *
 * <p>Parses the top-level command and options, resolves plugins (from the classpath, Maven
 * coordinates declared in {@code migraphe.yaml}, and the {@code plugins/} directory), loads the
 * {@link ExecutionContext} when needed, then dispatches to the matching {@link Command}
 * implementation. The command's exit code is returned to the operating system.
 */
public class Main {

    /** This class is not meant to be instantiated. */
    private Main() {}

    /**
     * Process entry point; runs the CLI and terminates the JVM with the resulting exit code.
     *
     * @param args the raw command-line arguments
     */
    public static void main(String[] args) {
        System.exit(run(args));
    }

    /**
     * Runs the CLI for the given arguments and returns an exit code without terminating the JVM.
     *
     * <p>Handles {@code -v}/{@code --version}, dispatches the {@code pin}, {@code validate}, {@code
     * generate}, {@code up}, {@code down}, and {@code status} commands, and converts any thrown
     * exception into a non-zero exit code via {@link #handleException(Exception)}.
     *
     * @param args the raw command-line arguments; the first element is the command name
     * @return the exit code: {@code 0} on success, a non-zero value on error or unknown command
     */
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

            // Determine the project directory (the current working directory).
            Path baseDir = Paths.get(System.getProperty("user.dir"));

            // The pin command runs before plugin resolution (it generates the lockfile).
            if ("pin".equals(commandName)) {
                boolean checkOnly = Arrays.asList(args).contains("--check");
                return new PluginPinCommand(baseDir, defaultMavenResolver(baseDir), checkOnly)
                        .execute();
            }

            // Resolve plugin dependencies via the Maven Resolver.
            PluginConfigParseResult parsed =
                    new PluginConfigPreParser().parse(baseDir.resolve("migraphe.yaml"));
            PluginResolver pluginResolver = new PluginResolver();
            URLClassLoader pluginClassLoader = pluginResolver.resolve(baseDir);

            // Initialize the PluginRegistry.
            PluginRegistry pluginRegistry =
                    initializePluginRegistry(baseDir, parsed, pluginClassLoader);

            // The validate command needs no ExecutionContext (offline validation).
            if ("validate".equals(commandName)) {
                ValidateCommand validateCommand =
                        new ValidateCommand(baseDir, pluginRegistry, parseEnvOption(args));
                return validateCommand.execute();
            }

            // The generate command needs the generator plugin class loader, so it builds its own
            // ExecutionContext rather than going through loadContext.
            if ("generate".equals(commandName)) {
                String nameFilter = parseNameOption(args);
                Path pluginsDir = resolvePluginsDir(baseDir, parsed);
                GenerateCommand generateCommand =
                        new GenerateCommand(
                                baseDir,
                                pluginRegistry,
                                pluginClassLoader,
                                nameFilter,
                                pluginsDir,
                                parseEnvOption(args));
                return generateCommand.execute();
            }

            // Load the ExecutionContext (honoring the --env option).
            ExecutionContext context = loadContext(baseDir, pluginRegistry, args);

            Command command = createCommand(commandName, args, context);

            if (command == null) {
                return 1;
            }

            return command.execute();

        } catch (Exception e) {
            return handleException(e);
        }
    }

    /**
     * Builds and populates the {@link PluginRegistry} from the classpath, the Maven-resolved plugin
     * class loader, and the {@code plugins/} directory (in that order).
     */
    private static PluginRegistry initializePluginRegistry(
            Path baseDir,
            PluginConfigParseResult parsed,
            @Nullable URLClassLoader pluginClassLoader) {
        PluginRegistry registry = new PluginRegistry();

        // 1. Load plugins from the classpath.
        registry.loadFromClasspath();

        // 2. Load plugins resolved via the Maven Resolver.
        if (pluginClassLoader != null) {
            registry.loadFromClassLoader(pluginClassLoader);
        }

        // 3. Load plugins from the plugins/ directory (backward compatibility).
        Path pluginsDir = resolvePluginsDir(baseDir, parsed);
        registry.loadFromDirectory(pluginsDir);

        return registry;
    }

    /**
     * Creates the {@link Command} instance matching the given command name, or {@code null} when
     * the name matches no command (reported here to standard error) or the matched command rejected
     * its arguments (reported by that command).
     */
    private static @Nullable Command createCommand(
            String commandName, String[] args, ExecutionContext context) {
        return switch (commandName) {
            case "up" -> createUpCommand(args, context);
            case "status" -> new StatusCommand(context);
            case "down" -> createDownCommand(args, context);
            default -> {
                System.err.println("Unknown command: " + commandName);
                printUsage();
                yield null;
            }
        };
    }

    /** Builds an {@link UpCommand} from the parsed arguments. */
    static Command createUpCommand(String[] args, ExecutionContext context) {
        List<String> argList = Arrays.asList(args);
        boolean skipConfirm = argList.contains("-y");
        boolean dryRun = parseDryRun(args);

        String targetId = firstPositionalArg(args);

        NodeId nodeId = targetId != null ? NodeId.of(targetId) : null;
        return new UpCommand(context, nodeId, skipConfirm, dryRun);
    }

    /**
     * Builds a {@link DownCommand} from the parsed arguments, or returns {@code null} after
     * printing an error when neither {@code --all} nor a target version is supplied.
     */
    static @Nullable Command createDownCommand(String[] args, ExecutionContext context) {
        List<String> argList = Arrays.asList(args);
        boolean skipConfirm = argList.contains("-y");
        boolean dryRun = parseDryRun(args);
        boolean allMigrations = argList.contains("--all");

        String version = firstPositionalArg(args);

        // A version is required unless --all is specified.
        if (!allMigrations && version == null) {
            System.err.println("Error: Version argument or --all required for 'down' command");
            System.err.println("Usage: migraphe down [-y] [--preview] [--all | <version>]");
            return null;
        }

        NodeId targetVersion = version != null ? NodeId.of(version) : null;
        return new DownCommand(context, targetVersion, allMigrations, skipConfirm, dryRun);
    }

    /**
     * Returns whether dry-run mode was requested. Both {@code --preview} and its legacy alias
     * {@code --dry-run} select it; the Gradle tasks expose only {@code --preview} because Gradle
     * reserves {@code --dry-run} for itself.
     *
     * @param args the raw command-line arguments
     * @return {@code true} when the plan should be printed without executing it
     */
    static boolean parseDryRun(String[] args) {
        List<String> argList = Arrays.asList(args);
        return argList.contains("--preview") || argList.contains("--dry-run");
    }

    /** Returns the value of the {@code --name} option, or {@code null} if absent. */
    private static @Nullable String parseNameOption(String[] args) {
        return parseValueOption(args, "--name");
    }

    /**
     * Returns the value of the {@code --env} option, or {@code null} if absent.
     *
     * @param args the raw command-line arguments
     * @return the environment name following {@code --env}, or {@code null}
     */
    static @Nullable String parseEnvOption(String[] args) {
        return parseValueOption(args, "--env");
    }

    /**
     * Loads the {@link ExecutionContext}, applying the environment overlay named by {@code --env}
     * if present.
     *
     * @param baseDir the project base directory
     * @param pluginRegistry the registry of loaded plugins
     * @param args the raw command-line arguments, scanned for {@code --env}
     * @return the loaded execution context
     */
    static ExecutionContext loadContext(
            Path baseDir, PluginRegistry pluginRegistry, String[] args) {
        String envName = parseEnvOption(args);
        return ExecutionContext.load(baseDir, pluginRegistry, envName);
    }

    /**
     * Returns the first positional argument, skipping the leading command word, value-bearing flags
     * (together with their values), and boolean flags. Returns {@code null} when none is present.
     *
     * @param args the raw command-line arguments
     * @return the first positional argument, or {@code null} if there is none
     */
    static @Nullable String firstPositionalArg(String[] args) {
        if (args.length == 0) {
            return null;
        }
        // Value-bearing flags (skip the flag and the following token).
        Set<String> valueFlags = Set.of("--env", "--name");
        // Boolean flags (skip the flag alone).
        Set<String> boolFlags = Set.of("-y", "--preview", "--dry-run", "--all");
        int i = 1; // args[0] is the command word, so skip it.
        while (i < args.length) {
            String a = args[i];
            if (valueFlags.contains(a)) {
                i += 2; // Skip the flag and its value.
            } else if (boolFlags.contains(a)) {
                i += 1;
            } else {
                return a;
            }
        }
        return null;
    }

    /**
     * Returns the argument following the given flag, or {@code null} if the flag is absent (or has
     * no following value).
     *
     * @param args the raw command-line arguments
     * @param flag the flag whose value should be read (e.g. {@code "--env"})
     * @return the value immediately after {@code flag}, or {@code null}
     */
    static @Nullable String parseValueOption(String[] args, String flag) {
        List<String> argList = Arrays.asList(args);
        for (int i = 0; i < argList.size() - 1; i++) {
            if (flag.equals(argList.get(i))) {
                return argList.get(i + 1);
            }
        }
        return null;
    }

    /**
     * Resolves the {@code plugins/} directory relative to the configured scan root (or the base
     * directory when no scan root is configured).
     *
     * @param baseDir the project base directory
     * @param parsed the pre-parsed plugin configuration, providing the optional scan root
     * @return the path to the {@code plugins/} directory
     */
    static Path resolvePluginsDir(Path baseDir, PluginConfigParseResult parsed) {
        Path scanRoot = parsed.scanRoot().map(baseDir::resolve).orElse(baseDir);
        return scanRoot.resolve("plugins");
    }

    /**
     * Decides whether a stack trace should be printed for the given exception. User-facing,
     * expected errors ({@link IllegalArgumentException}, {@link PluginResolutionException}) are
     * reported with their message only.
     *
     * @param e the exception that terminated a command
     * @return {@code true} to print the full stack trace, {@code false} to print only the message
     */
    static boolean shouldPrintStackTrace(Exception e) {
        return !(e instanceof IllegalArgumentException || e instanceof PluginResolutionException);
    }

    /** Builds the default Maven resolver, with Maven Central plus any repositories from config. */
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

    /** Resolves the local Maven repository, honoring {@code maven.repo.local} or {@code ~/.m2}. */
    private static Path defaultLocalRepo() {
        String m2Home = System.getProperty("maven.repo.local");
        if (m2Home != null) {
            return Path.of(m2Home);
        }
        return Path.of(System.getProperty("user.home"), ".m2", "repository");
    }

    /**
     * Reports an exception to standard error and converts it into an exit code. The stack trace is
     * printed only when {@link #shouldPrintStackTrace(Exception)} returns {@code true}.
     *
     * @param e the exception that terminated a command
     * @return the error exit code {@code 1}
     */
    static int handleException(Exception e) {
        System.err.println("Error: " + e.getMessage());
        if (shouldPrintStackTrace(e)) {
            e.printStackTrace();
        }
        return 1;
    }

    /** Prints the CLI usage/help text to standard output. */
    private static void printUsage() {
        System.out.println("Migraphe - Database Migration Tool");
        System.out.println();
        System.out.println("Usage: migraphe <command> [options]");
        System.out.println("       migraphe -v | --version");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  up [-y] [--preview] [<id>]          Execute migrations");
        System.out.println("  down [-y] [--preview] [--all | <v>] Rollback migrations");
        System.out.println("  status                              Show migration status");
        System.out.println(
                "  validate                            Validate configuration (offline)");
        System.out.println("  generate [--name <name>]            Run generators");
        System.out.println(
                "  pin [--check]                       Generate or verify migraphe.lock.yaml");
        System.out.println();
        System.out.println("Common options (up, down, status, validate, generate):");
        System.out.println(
                "  --env <name>   Apply the environments/<name>.yaml overlay. Overrides target");
        System.out.println(
                "                 settings only; it does not partition migration history.");
        System.out.println();
        System.out.println("Up options:");
        System.out.println("  <id>           Execute migrations up to and including <id>");
        System.out.println("  -y             Skip confirmation prompt");
        System.out.println("  --preview      Show plan without executing");
        System.out.println();
        System.out.println("Down options:");
        System.out.println("  <version>      Rollback migrations that depend on <version>");
        System.out.println("  --all          Rollback all executed migrations");
        System.out.println("  -y             Skip confirmation prompt");
        System.out.println("  --preview      Show plan without executing");
        System.out.println();
        System.out.println("Generate options:");
        System.out.println("  --name <name>  Run only the generator with matching name");
        System.out.println();
    }
}
