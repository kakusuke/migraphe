package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/**
 * Detailed information about a single database schema.
 *
 * <p>One instance is produced per schema discovered through {@link java.sql.DatabaseMetaData}. The
 * generic JDBC provider populates {@code tables} and {@code views}; the remaining object kinds
 * (routines, triggers, sequences, user-defined types) are left empty by the generic path and may be
 * filled in by dialect-specific providers (PostgreSQL, MySQL).
 *
 * @param name the schema name (the {@code TABLE_SCHEM} value from {@link
 *     java.sql.DatabaseMetaData#getSchemas()})
 * @param tables the tables contained in this schema
 * @param views the views contained in this schema
 * @param routines the stored procedures and functions in this schema; empty unless populated by a
 *     dialect-specific provider
 * @param triggers the triggers in this schema; empty unless populated by a dialect-specific
 *     provider
 * @param sequences the sequences in this schema; empty unless populated by a dialect-specific
 *     provider
 * @param udts the user-defined types in this schema; empty unless populated by a dialect-specific
 *     provider
 */
public record JdbcSchemaDetail(
        String name,
        List<JdbcTableInfo> tables,
        List<JdbcViewInfo> views,
        List<JdbcRoutineInfo> routines,
        List<JdbcTriggerInfo> triggers,
        List<JdbcSequenceInfo> sequences,
        List<JdbcUdtInfo> udts) {}
