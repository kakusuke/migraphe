package io.github.kakusuke.migraphe.core.plugin;

import java.util.Set;

/**
 * 指定されたタイプのプラグインが見つからない場合にスローされる例外。
 *
 * <p>利用可能なプラグイン一覧と解決方法を含む詳細なエラーメッセージを提供する。
 */
public final class PluginNotFoundException extends RuntimeException {

    private final String requestedType;
    private final Set<String> availableTypes;

    public PluginNotFoundException(String requestedType, Set<String> availableTypes) {
        super(buildMessage(requestedType, availableTypes));
        this.requestedType = requestedType;
        this.availableTypes = Set.copyOf(availableTypes);
    }

    private static String buildMessage(String requestedType, Set<String> availableTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append("No plugin found for type '").append(requestedType).append("'.");

        if (availableTypes.isEmpty()) {
            sb.append("\nNo plugins are currently loaded.");
        } else {
            sb.append("\nAvailable plugins: ").append(availableTypes);
        }

        sb.append("\n\nTo use this plugin type:");
        sb.append("\n  1. Place the plugin JAR file in ./plugins/ directory");
        sb.append(
                "\n"
                        + "  2. Ensure the JAR contains"
                        + " META-INF/services/io.github.kakusuke.migraphe.api.spi.MigraphePlugin");

        return sb.toString();
    }

    public String requestedType() {
        return requestedType;
    }

    public Set<String> availableTypes() {
        return availableTypes;
    }
}
