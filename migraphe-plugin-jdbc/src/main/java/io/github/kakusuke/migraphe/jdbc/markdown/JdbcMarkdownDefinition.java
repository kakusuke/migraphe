package io.github.kakusuke.migraphe.jdbc.markdown;

import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;

/**
 * JDBC Markdown ドキュメント生成の定義。
 *
 * <p>YAML ファイルからマッピングされ、スキーマ情報を Markdown 形式で出力する設定を保持する。
 */
@ConfigMapping(prefix = "")
public interface JdbcMarkdownDefinition extends GeneratorDefinition {

    String type();

    /** データベース名。 */
    String name();

    /**
     * 出力ディレクトリ。
     *
     * @return 出力先ディレクトリパス（デフォルト: "docs/schema"）
     */
    @WithDefault("docs/schema")
    String outputDir();

    /**
     * 除外パターンのリスト。
     *
     * @return 除外パターン、指定なしの場合は空
     */
    Optional<List<ExcludePattern>> excludes();

    /** スキーマまたはテーブルの除外パターン。 */
    interface ExcludePattern {

        /** 除外対象のスキーマ名。 */
        Optional<String> schema();

        /** 除外対象のテーブル名。 */
        Optional<String> table();
    }
}
