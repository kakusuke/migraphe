package io.github.kakusuke.migraphe.gradle;

import org.gradle.api.file.DirectoryProperty;

/** migraphe プラグインの DSL 拡張。 */
public abstract class MigrapheExtension {

    /** プロジェクト設定のベースディレクトリ（デフォルト: プロジェクトディレクトリ）。 */
    public abstract DirectoryProperty getBaseDir();
}
