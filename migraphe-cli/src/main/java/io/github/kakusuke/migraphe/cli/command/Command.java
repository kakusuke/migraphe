package io.github.kakusuke.migraphe.cli.command;

/**
 * CLIコマンドのインターフェース。
 *
 * <p>各コマンド（up, down, status など）はこのインターフェースを実装する。
 */
public interface Command {

    /**
     * コマンドを実行する。
     *
     * @return 終了コード（0: 成功, 非0: エラー）
     */
    int execute();
}
