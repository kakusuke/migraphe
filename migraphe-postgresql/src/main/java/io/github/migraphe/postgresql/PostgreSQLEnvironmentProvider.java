package io.github.migraphe.postgresql;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.environment.EnvironmentConfig;
import io.github.migraphe.api.spi.EnvironmentProvider;

/** PostgreSQL Environment を生成する Provider。 */
public final class PostgreSQLEnvironmentProvider implements EnvironmentProvider {

    @Override
    public Environment createEnvironment(String name, EnvironmentConfig config) {
        // EnvironmentConfig から必要な設定を取得
        String jdbcUrl =
                config.getProperty("jdbc_url")
                        .orElseThrow(
                                () ->
                                        new PostgreSQLException(
                                                "Missing required config: jdbc_url for environment:"
                                                        + " "
                                                        + name));

        String username =
                config.getProperty("username")
                        .orElseThrow(
                                () ->
                                        new PostgreSQLException(
                                                "Missing required config: username for environment:"
                                                        + " "
                                                        + name));

        String password =
                config.getProperty("password")
                        .orElseThrow(
                                () ->
                                        new PostgreSQLException(
                                                "Missing required config: password for environment:"
                                                        + " "
                                                        + name));

        return PostgreSQLEnvironment.create(name, jdbcUrl, username, password);
    }
}
