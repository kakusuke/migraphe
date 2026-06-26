package io.github.kakusuke.migraphe.core.common;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of a validation check.
 *
 * <p>An internal core-side value object (not part of the plugin SPI) used to report whether a check
 * — for example {@link io.github.kakusuke.migraphe.core.graph.MigrationGraph#validate() graph
 * validation} — succeeded, along with any error messages explaining the failures. A valid result
 * carries no errors; an invalid result carries at least one. Construct instances via the {@link
 * #valid()} and {@link #invalid(List)}/{@link #invalid(String)} factory methods.
 *
 * @param isValid {@code true} if validation passed with no errors, {@code false} otherwise
 * @param errors the error messages describing each validation failure; empty when {@code isValid}
 *     is {@code true}. Defensively copied to an immutable list.
 */
public record ValidationResult(boolean isValid, List<String> errors) {

    /**
     * Canonical constructor that defensively copies {@code errors} into an immutable list.
     *
     * @param isValid whether validation passed
     * @param errors the error messages; must not be {@code null}
     * @throws NullPointerException if {@code errors} is {@code null}
     */
    public ValidationResult {
        Objects.requireNonNull(errors, "errors must not be null");
        errors = List.copyOf(errors);
    }

    /**
     * Creates a successful result with no errors.
     *
     * @return a valid {@code ValidationResult}
     */
    public static ValidationResult valid() {
        return new ValidationResult(true, List.of());
    }

    /**
     * Creates a failed result from the given error messages.
     *
     * @param errors the non-empty list of error messages describing the failures
     * @return an invalid {@code ValidationResult} carrying {@code errors}
     * @throws NullPointerException if {@code errors} is {@code null}
     * @throws IllegalArgumentException if {@code errors} is empty
     */
    public static ValidationResult invalid(List<String> errors) {
        Objects.requireNonNull(errors, "errors must not be null");
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid result must have at least one error");
        }
        return new ValidationResult(false, errors);
    }

    /**
     * Creates a failed result from a single error message.
     *
     * @param error the error message describing the failure
     * @return an invalid {@code ValidationResult} carrying the single error
     */
    public static ValidationResult invalid(String error) {
        return invalid(List.of(error));
    }
}
