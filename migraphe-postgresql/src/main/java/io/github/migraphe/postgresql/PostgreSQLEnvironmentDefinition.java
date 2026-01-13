package io.github.migraphe.postgresql;

import io.github.migraphe.api.spi.EnvironmentDefinition;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * PostgreSQL 用の EnvironmentDefinition サブタイプ。
 *
 * <p>YAML ファイルから直接マッピングされる。
 *
 * <p>YAML 例:
 *
 * <pre>{@code
 * target:
 *   db1:
 *     type: postgresql
 *     jdbc_url: jdbc:postgresql://localhost:5432/mydb
 *     username: dbuser
 *     password: secret
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
