package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.cli.util.AnsiColor;
import io.github.kakusuke.migraphe.core.config.ConfigValidator;
import io.github.kakusuke.migraphe.core.config.ConfigValidator.ValidationOutput;
import io.github.kakusuke.migraphe.core.config.YamlFileScanner;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 設定ファイルをオフラインで検証するコマンド。 DB接続なしで全エラーを蓄積して一括表示。 */
public class ValidateCommand implements Command {

    private final Path baseDir;
    private final PluginRegistry pluginRegistry;
    private final boolean colorEnabled;

    public ValidateCommand(Path baseDir, PluginRegistry pluginRegistry) {
        this(baseDir, pluginRegistry, AnsiColor.isColorEnabled());
    }

    /** テスト用コンストラクタ。 */
    public ValidateCommand(Path baseDir, PluginRegistry pluginRegistry, boolean colorEnabled) {
        this.baseDir = baseDir;
        this.pluginRegistry = pluginRegistry;
        this.colorEnabled = colorEnabled;
    }

    @Override
    public int execute() {
        printHeader();

        ConfigValidator validator = new ConfigValidator(pluginRegistry);
        ValidationOutput result = validator.validate(baseDir);

        // 各チェックステップを表示
        displayCheckResults(result);

        // サマリーを表示
        if (result.isValid()) {
            System.out.println();
            printSuccess("Validation successful.");
            return 0;
        } else {
            System.out.println();
            int errorCount = result.errors().size();
            printError(
                    "Validation failed with "
                            + errorCount
                            + " error"
                            + (errorCount == 1 ? "" : "s")
                            + ".");
            return 1;
        }
    }

    private void printHeader() {
        System.out.println("Validation");
        System.out.println("==========");
        System.out.println();
    }

    private void displayCheckResults(ValidationOutput result) {
        YamlFileScanner scanner = new YamlFileScanner();
        List<String> errors = result.errors();

        // 1. Project configuration
        List<String> projectErrors = filterErrors(errors, "migraphe.yaml");
        printCheckResult(
                "Checking project configuration...", projectErrors.isEmpty(), projectErrors);

        // 早期終了（migraphe.yaml がない場合）
        if (!projectErrors.isEmpty() && errors.stream().anyMatch(e -> e.contains("not found"))) {
            return;
        }

        // 2. Targets
        int targetCount = scanner.scanTargetFiles(baseDir).size();
        List<String> targetErrors = filterErrors(errors, "targets/");
        String targetLabel =
                "Checking targets ("
                        + targetCount
                        + " file"
                        + (targetCount == 1 ? "" : "s")
                        + ")...";
        printCheckResult(targetLabel, targetErrors.isEmpty(), targetErrors);

        // 3. Tasks
        int taskCount = scanner.scanTaskFiles(baseDir).size();
        List<String> taskFileErrors = filterTaskFileErrors(errors);
        String taskLabel =
                "Checking tasks (" + taskCount + " file" + (taskCount == 1 ? "" : "s") + ")...";
        printCheckResult(taskLabel, taskFileErrors.isEmpty(), taskFileErrors);

        // 4. Dependencies
        List<String> depErrors = filterErrors(errors, "Dependency");
        printCheckResult("Checking dependencies...", depErrors.isEmpty(), depErrors);

        // 5. Graph structure
        List<String> cycleErrors = filterErrors(errors, "Circular");
        printCheckResult("Checking graph structure...", cycleErrors.isEmpty(), cycleErrors);
    }

    private List<String> filterErrors(List<String> errors, String keyword) {
        List<String> filtered = new ArrayList<>();
        for (String error : errors) {
            if (error.contains(keyword)) {
                filtered.add(error);
            }
        }
        return filtered;
    }

    private List<String> filterTaskFileErrors(List<String> errors) {
        List<String> filtered = new ArrayList<>();
        for (String error : errors) {
            if (error.startsWith("tasks/") && !error.contains("Dependency")) {
                filtered.add(error);
            }
        }
        return filtered;
    }

    private void printCheckResult(String label, boolean success, List<String> errors) {
        if (success) {
            String ok = colorEnabled ? AnsiColor.green("OK") : "OK";
            System.out.println(label + " " + ok);
        } else {
            String fail = colorEnabled ? AnsiColor.red("FAIL") : "FAIL";
            System.out.println(label + " " + fail);
            for (String error : errors) {
                String marker = colorEnabled ? AnsiColor.red("×") : "×";
                System.out.println("  " + marker + " " + error);
            }
        }
    }

    private void printSuccess(String message) {
        if (colorEnabled) {
            System.out.println(AnsiColor.green(message));
        } else {
            System.out.println(message);
        }
    }

    private void printError(String message) {
        if (colorEnabled) {
            System.out.println(AnsiColor.red(message));
        } else {
            System.out.println(message);
        }
    }
}
