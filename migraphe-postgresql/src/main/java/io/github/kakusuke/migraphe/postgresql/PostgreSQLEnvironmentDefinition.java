package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * PostgreSQL 用の EnvironmentDefinition サブタイプ。
 *
 * <p>YAML ファイルから直接マッピングされる。ターゲット名はファイル名から導出される。
 *
 * <p>YAML 例 (targets/db1.yaml):
 *
 * <pre>{@code
 * type: postgresql
 * jdbc_url: jdbc:postgresql://localhost:5432/mydb
 * username: dbuser
 * password: secret
 * }</pre>
 */
@ConfigMapping(prefix = "")
public interface PostgreSQLEnvironmentDefinition extends EnvironmentDefinition {

    @Override
    String type();

    @WithName("jdbc_url")
    String jdbcUrl();

    String username();

    String password();
}
