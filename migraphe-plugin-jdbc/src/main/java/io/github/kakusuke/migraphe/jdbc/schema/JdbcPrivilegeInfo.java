package io.github.kakusuke.migraphe.jdbc.schema;

/** テーブル権限情報。 */
public record JdbcPrivilegeInfo(
        String grantor, String grantee, String privilege, boolean grantable) {}
