package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import org.jspecify.annotations.Nullable;

/**
 * グラフ描画時の各ノードの行情報。
 *
 * <p>CLI は status を色付けして表示、Gradle はそのままログ出力する。
 */
public record NodeLineInfo(
        MigrationNode node,
        String graphPrefix,
        @Nullable String mergeLine,
        @Nullable String branchLine,
        @Nullable String connectorLine) {

    /**
     * ノード行のプレーンテキストを生成する。
     *
     * @param status ステータス文字列（例: "[OK]", "[ ]", "[✓]"）
     * @return graphPrefix + status + id + name の形式
     */
    public String toPlainText(String status) {
        return graphPrefix + " " + status + " " + node.id().value() + " - " + node.name();
    }
}
