package io.github.migraphe.api.common;

import java.util.List;
import java.util.Objects;

/** バリデーション結果。 */
public record ValidationResult(boolean isValid, List<String> errors) {

    public ValidationResult {
        Objects.requireNonNull(errors, "errors must not be null");
        errors = List.copyOf(errors);
    }

    public static ValidationResult valid() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult invalid(List<String> errors) {
        Objects.requireNonNull(errors, "errors must not be null");
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid result must have at least one error");
        }
        return new ValidationResult(false, errors);
    }

    public static ValidationResult invalid(String error) {
        return invalid(List.of(error));
    }
}
