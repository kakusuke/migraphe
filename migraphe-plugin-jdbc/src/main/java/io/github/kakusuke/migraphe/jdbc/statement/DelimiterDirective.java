package io.github.kakusuke.migraphe.jdbc.statement;

import org.jspecify.annotations.Nullable;

/**
 * セグメント先頭で区切り文字の変更指示（例: MySQL の {@code DELIMITER} ディレクティブ）を検出するパーサー。
 *
 * <p>{@link StatementSplitter} は各セグメントのトリビアスキップ後にこれを適用し、検出された場合は その指示自体を出力に含めず、以降の区切り文字を {@link
 * Result#newDelimiter()} に切り替える。
 */
public interface DelimiterDirective {

    /**
     * {@code pos} から区切り変更指示を検出する。
     *
     * @param sql 解析対象の SQL テキスト
     * @param pos 検出を試みる位置（トリビアスキップ後のセグメント先頭）
     * @return 検出時は変更後の区切り文字と次の位置、非検出時は {@code null}
     */
    @Nullable Result detect(String sql, int pos);

    /**
     * 区切り変更指示の検出結果。
     *
     * @param newDelimiter 変更後の区切り文字
     * @param nextPos 指示を消費した直後の位置
     */
    record Result(String newDelimiter, int nextPos) {}
}
