package io.github.kakusuke.migraphe.jdbc;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One step of a history-schema initialization script: a detection query paired with the statements
 * that apply the step.
 *
 * <p>{@link JdbcHistoryRepository#initialize()} runs {@link #checkSql()} first and executes {@link
 * #applySql()} only when the query returns no rows. Steps are therefore idempotent by construction
 * and need no schema-version bookkeeping.
 *
 * @param label a human-readable name for the step. It appears in exactly two places — the {@code
 *     Failed to apply schema step '<label>'} and {@code Failed to detect schema step '<label>'}
 *     messages {@link JdbcHistoryRepository} raises — and is never persisted, matched or compared,
 *     which is why {@link SchemaStepParser} lets two steps share one
 * @param checkSql the detection query, or {@code null} for an unconditional step whose statements
 *     are always executed
 * @param applySql the statements applying this step, in order
 */
record SchemaStep(String label, @Nullable String checkSql, List<String> applySql) {}
