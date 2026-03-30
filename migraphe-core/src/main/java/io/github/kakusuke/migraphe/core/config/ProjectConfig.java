package io.github.kakusuke.migraphe.core.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;

/**
 * プロジェクト全体の設定。
 *
 * <p>YAMLファイル（application.yaml）からMicroProfile Configで読み込まれる。
 */
@ConfigMapping(prefix = "")
public interface ProjectConfig {

    /**
     * プロジェクト情報セクション。
     *
     * @return プロジェクト情報
     */
    ProjectSection project();

    /**
     * 履歴管理設定セクション。
     *
     * @return 履歴管理設定
     */
    HistorySection history();

    /**
     * 実行設定セクション。
     *
     * @return 実行設定
     */
    ExecutionSection execution();

    /**
     * ジェネレーター設定リスト。
     *
     * @return ジェネレーター設定（未設定の場合は空）
     */
    Optional<List<GeneratorSection>> generators();

    /** プロジェクト情報。 */
    interface ProjectSection {
        /**
         * プロジェクト名。
         *
         * @return プロジェクト名
         */
        String name();
    }

    /** 履歴管理設定。 */
    interface HistorySection {
        /**
         * 履歴を保存するターゲットID。
         *
         * @return ターゲットID
         */
        String target();
    }

    /** 実行設定。 */
    interface ExecutionSection {
        /**
         * 並列実行を有効にするかどうか。
         *
         * @return 並列実行が有効の場合 true
         */
        @WithDefault("false")
        boolean parallel();

        /**
         * 最大並列数。0 の場合は無制限。
         *
         * @return 最大並列数
         */
        @WithDefault("0")
        int maxParallelism();
    }

    /** ジェネレーター設定。 */
    interface GeneratorSection {
        String name();

        String type();

        String target();

        SourceSection source();

        @WithDefault("docs/schema")
        String outputDir();

        Optional<List<ExcludeSection>> excludes();

        /** ソースプラグイン設定。 */
        interface SourceSection {
            Optional<String> type();

            Optional<String> target();
        }

        /** 除外パターン。 */
        interface ExcludeSection {
            Optional<String> schema();

            Optional<String> table();
        }
    }
}
