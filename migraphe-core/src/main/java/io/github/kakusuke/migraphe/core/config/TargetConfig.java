package io.github.kakusuke.migraphe.core.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * ターゲット（データベース接続など）の設定。
 *
 * <p>YAMLファイル（targets/*.yaml）からMicroProfile Configで読み込まれる。
 */
@ConfigMapping(prefix = "")
public interface TargetConfig {

    /**
     * ターゲットのタイプ（例: "postgresql", "mysql"）。
     *
     * @return ターゲットタイプ
     */
    String type();

    /**
     * JDBC URL。
     *
     * <p>YAML内では {@code jdbc_url} として定義される。
     *
     * @return JDBC URL
     */
    @WithName("jdbc_url")
    String jdbcUrl();

    /**
     * ユーザー名。
     *
     * @return ユーザー名
     */
    String username();

    /**
     * パスワード。
     *
     * @return パスワード
     */
    String password();
}
