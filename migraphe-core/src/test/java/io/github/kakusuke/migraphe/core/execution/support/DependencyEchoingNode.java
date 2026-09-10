package io.github.kakusuke.migraphe.core.execution.support;

import io.github.kakusuke.migraphe.api.graph.Fingerprinter;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;

/**
 * 渡された {@link Fingerprinter} をそのまま使うノード。
 *
 * <p>以前は「core が計算した閉包がノードまで届いているか」を測るために、渡されたリストを返していた。 ノードは閉包を見なくなったので、いまは「ノードが受け取った fingerprinter
 * を使っているか」を測る — 差し替えられた fingerprinter を無視する実装は、このノードを使ったテストで落ちる。
 */
public record DependencyEchoingNode(MigrationNode delegate) implements DelegatingMigrationNode {

    @Override
    public String fingerprint(Fingerprinter fingerprinter) {
        return fingerprinter.over("echoed");
    }
}
