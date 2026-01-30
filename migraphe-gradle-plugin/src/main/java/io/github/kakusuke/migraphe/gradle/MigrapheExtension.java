package io.github.kakusuke.migraphe.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;

/** migraphe プラグインの DSL 拡張。 */
public abstract class MigrapheExtension {

    /** プロジェクト設定のベースディレクトリ（デフォルト: プロジェクトディレクトリ）。 */
    public abstract DirectoryProperty getBaseDir();

    /** SmallRye Config に差し込む変数マップ（デフォルト: 空マップ）。 */
    public abstract MapProperty<String, String> getVariables();
}
