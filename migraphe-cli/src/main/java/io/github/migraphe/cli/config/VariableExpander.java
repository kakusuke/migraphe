package io.github.migraphe.cli.config;

import io.github.migraphe.cli.exception.ConfigurationException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 変数展開（${VAR}構文）を処理するユーティリティクラス。
 *
 * <p>ネストした変数展開をサポートし、循環参照を検出します。
 */
public final class VariableExpander {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_]+)\\}");
    private static final int MAX_EXPANSION_DEPTH = 10;

    private VariableExpander() {
        // ユーティリティクラス - インスタンス化不可
    }

    /**
     * 入力文字列内の全ての ${VAR} 参照を展開します。
     *
     * @param input 展開対象の文字列（${VAR}参照を含む可能性あり）
     * @param variables 環境変数マップ
     * @return 展開後の文字列
     * @throws ConfigurationException 変数が未定義の場合、または循環参照が検出された場合
     */
    public static String expand(String input, Map<String, String> variables) {
        if (input == null || !input.contains("${")) {
            return input;
        }

        String result = input;
        int depth = 0;

        while (containsVariableReference(result)) {
            if (depth++ > MAX_EXPANSION_DEPTH) {
                throw new ConfigurationException(
                        "Maximum expansion depth exceeded (possible circular reference): " + input);
            }

            String previous = result;
            result = expandOnce(result, variables);

            if (result.equals(previous)) {
                // 進捗なし - 未定義変数
                Matcher matcher = VARIABLE_PATTERN.matcher(result);
                if (matcher.find()) {
                    throw new ConfigurationException("Undefined variable: ${" + matcher.group(1) + "}");
                }
                break;
            }
        }

        return result;
    }

    private static String expandOnce(String input, Map<String, String> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = variables.get(varName);

            if (value == null) {
                throw new ConfigurationException("Undefined variable: ${" + varName + "}");
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static boolean containsVariableReference(String input) {
        return VARIABLE_PATTERN.matcher(input).find();
    }
}
