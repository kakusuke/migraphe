package io.github.kakusuke.migraphe.core.execution.support;

import io.github.kakusuke.migraphe.api.graph.Fingerprinter;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import org.jspecify.annotations.Nullable;

/**
 * fingerprint を返すノードを作るためのラッパー。
 *
 * <p>{@code SimpleMigrationNode} の fingerprint は builder に渡された内容から決まるので、テストが期待値を書きたいときには扱いにくい。
 * 委譲しつつ fingerprint だけを差し替えることで、比較対象の値をテスト側が決められるようにする。{@code null} を渡すと、 記録された token を持たないノード ——
 * 履歴の行から作られたノードが唯一そうなる —— を模せる。
 */
public record FingerprintedNode(MigrationNode delegate, @Nullable String fingerprint)
        implements DelegatingMigrationNode {

    @Override
    public @Nullable String fingerprint(Fingerprinter fingerprinter) {
        return fingerprint;
    }
}
