package io.github.kakusuke.migraphe.cli.util;

import java.util.regex.Pattern;

/** ANSI エスケープコードを使ったカラー出力ユーティリティ。 */
public final class AnsiColor {

    /** エスケープコード除去用の正規表現パターン。 */
    private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile("\u001B\\[[0-9;]*m");

    public static final String RESET = "\u001B[0m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String RED = "\u001B[31m";
    public static final String CYAN = "\u001B[36m";
    public static final String BOLD = "\u001B[1m";

    private AnsiColor() {
        // ユーティリティクラス
    }

    /** テキストを緑色でラップする。 */
    public static String green(String text) {
        return GREEN + text + RESET;
    }

    /** テキストを黄色でラップする。 */
    public static String yellow(String text) {
        return YELLOW + text + RESET;
    }

    /** テキストを赤色でラップする。 */
    public static String red(String text) {
        return RED + text + RESET;
    }

    /** テキストをシアン色でラップする。 */
    public static String cyan(String text) {
        return CYAN + text + RESET;
    }

    /** テキストを太字でラップする。 */
    public static String bold(String text) {
        return BOLD + text + RESET;
    }

    /** ANSI エスケープコードを除去する。 */
    public static String stripColors(String text) {
        return ANSI_ESCAPE_PATTERN.matcher(text).replaceAll("");
    }

    /**
     * 条件付きでテキストに色をつける。
     *
     * @param text 対象テキスト
     * @param color ANSI カラーコード
     * @param colorEnabled 色を有効にするか
     * @return 色付きまたはプレーンテキスト
     */
    public static String colorize(String text, String color, boolean colorEnabled) {
        if (colorEnabled) {
            return color + text + RESET;
        }
        return text;
    }

    /**
     * 色出力が有効かどうかを判定する。
     *
     * <p>NO_COLOR 環境変数が設定されていない かつ コンソールが利用可能な場合に true。
     *
     * @return 色出力が有効なら true
     */
    public static boolean isColorEnabled() {
        return System.getenv("NO_COLOR") == null && System.console() != null;
    }
}
