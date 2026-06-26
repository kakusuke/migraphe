package io.github.kakusuke.migraphe.jdbc.schema;

/**
 * Table-level privilege (access grant) information.
 *
 * <p>Immutable data holder describing a single privilege granted on a table, corresponding to a row
 * from {@link java.sql.DatabaseMetaData#getTablePrivileges}.
 *
 * @param grantor the principal that granted the privilege, or an empty string if unknown
 * @param grantee the principal the privilege was granted to
 * @param privilege the privilege name (for example {@code "SELECT"}, {@code "INSERT"}, {@code
 *     "UPDATE"})
 * @param grantable {@code true} if {@code grantee} may in turn grant this privilege to others
 */
public record JdbcPrivilegeInfo(
        String grantor, String grantee, String privilege, boolean grantable) {}
