package io.github.kakusuke.migraphe.core.config;

import io.smallrye.config.ConfigMapping;

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
}
