package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import java.util.Optional;

/**
 * 汎用 JDBC 用の EnvironmentDefinition。
 *
 * <p>YAML 例 (targets/mydb.yaml):
 *
 * <pre>{@code
 * type: jdbc
 * driver_class: org.mariadb.jdbc.Driver
 * db_label: MariaDB
 * jdbc_url: jdbc:mariadb://localhost:3306/myapp
 * username: user
 * password: secret
 * }</pre>
 */
@ConfigMapping(prefix = "")
public interface JdbcEnvironmentDefinition extends EnvironmentDefinition {

    @Override
    String type();

    @WithName("jdbc_url")
    String jdbcUrl();

    String username();

    Optional<String> password();

    @WithName("driver_class")
    String driverClass();

    @WithName("db_label")
    Optional<String> dbLabel();
}
