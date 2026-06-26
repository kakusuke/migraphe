package io.github.kakusuke.migraphe.api.common;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A type-safe result that holds either a success value or an error, in the spirit of
 * railway-oriented programming.
 *
 * <p>This sealed interface has exactly two implementations: {@link Ok}, which carries a success
 * value, and {@link Err}, which carries an error. It is used throughout Migraphe — notably by
 * {@link io.github.kakusuke.migraphe.api.task.Task#execute()} — to report success or failure
 * without throwing, so that callers can branch on the outcome explicitly. Both variants are
 * immutable and disallow {@code null} values.
 *
 * @param <T> the type of the success value
 * @param <E> the type of the error value
 * @see io.github.kakusuke.migraphe.api.task.Task
 */
public sealed interface Result<T, E> permits Result.Ok, Result.Err {

    /**
     * Indicates whether this result represents success.
     *
     * @return {@code true} if this is an {@link Ok}, {@code false} otherwise
     */
    boolean isOk();

    /**
     * Indicates whether this result represents an error.
     *
     * @return {@code true} if this is an {@link Err}, {@code false} otherwise
     */
    boolean isErr();

    /**
     * Returns the success value held by this result.
     *
     * @return the value when this is an {@link Ok}, or {@code null} when this is an {@link Err}
     */
    @Nullable T value();

    /**
     * Returns the error value held by this result.
     *
     * @return the error when this is an {@link Err}, or {@code null} when this is an {@link Ok}
     */
    @Nullable E error();

    /**
     * Transforms the success value while leaving an error untouched.
     *
     * @param mapper the function applied to the success value when this is an {@link Ok}
     * @param <U> the type of the mapped success value
     * @return an {@link Ok} wrapping the mapped value, or this result's error unchanged as an
     *     {@link Err}
     */
    <U> Result<U, E> map(Function<T, U> mapper);

    /**
     * Transforms the error value while leaving a success untouched.
     *
     * @param mapper the function applied to the error value when this is an {@link Err}
     * @param <F> the type of the mapped error value
     * @return an {@link Err} wrapping the mapped error, or this result's success value unchanged as
     *     an {@link Ok}
     */
    <F> Result<T, F> mapError(Function<E, F> mapper);

    /**
     * The success variant of {@link Result}, carrying a non-{@code null} value.
     *
     * @param <T> the type of the success value
     * @param <E> the type of the error value
     */
    final class Ok<T, E> implements Result<T, E> {
        private final T val;

        /**
         * Creates a successful result.
         *
         * @param val the success value; must be non-{@code null}
         * @throws NullPointerException if {@code val} is {@code null}
         */
        public Ok(T val) {
            this.val = Objects.requireNonNull(val, "value must not be null");
        }

        @Override
        public boolean isOk() {
            return true;
        }

        @Override
        public boolean isErr() {
            return false;
        }

        @Override
        public T value() {
            return val;
        }

        @Override
        public @Nullable E error() {
            return null;
        }

        @Override
        public <U> Result<U, E> map(Function<T, U> mapper) {
            return new Ok<>(mapper.apply(val));
        }

        @Override
        public <F> Result<T, F> mapError(Function<E, F> mapper) {
            return new Ok<>(val);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Ok<?, ?> other)) return false;
            return Objects.equals(val, other.val);
        }

        @Override
        public int hashCode() {
            return Objects.hash(val);
        }

        @Override
        public String toString() {
            return "Ok[" + val + "]";
        }
    }

    /**
     * The error variant of {@link Result}, carrying a non-{@code null} error.
     *
     * @param <T> the type of the success value
     * @param <E> the type of the error value
     */
    final class Err<T, E> implements Result<T, E> {
        private final E err;

        /**
         * Creates an error result.
         *
         * @param err the error value; must be non-{@code null}
         * @throws NullPointerException if {@code err} is {@code null}
         */
        public Err(E err) {
            this.err = Objects.requireNonNull(err, "error must not be null");
        }

        @Override
        public boolean isOk() {
            return false;
        }

        @Override
        public boolean isErr() {
            return true;
        }

        @Override
        public @Nullable T value() {
            return null;
        }

        @Override
        public E error() {
            return err;
        }

        @Override
        public <U> Result<U, E> map(Function<T, U> mapper) {
            return new Err<>(err);
        }

        @Override
        public <F> Result<T, F> mapError(Function<E, F> mapper) {
            return new Err<>(mapper.apply(err));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Err<?, ?> other)) return false;
            return Objects.equals(err, other.err);
        }

        @Override
        public int hashCode() {
            return Objects.hash(err);
        }

        @Override
        public String toString() {
            return "Err[" + err + "]";
        }
    }

    /**
     * Creates a successful {@link Result}.
     *
     * @param value the success value; must be non-{@code null}
     * @param <T> the type of the success value
     * @param <E> the type of the error value
     * @return an {@link Ok} wrapping {@code value}
     * @throws NullPointerException if {@code value} is {@code null}
     */
    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    /**
     * Creates an error {@link Result}.
     *
     * @param error the error value; must be non-{@code null}
     * @param <T> the type of the success value
     * @param <E> the type of the error value
     * @return an {@link Err} wrapping {@code error}
     * @throws NullPointerException if {@code error} is {@code null}
     */
    static <T, E> Result<T, E> err(E error) {
        return new Err<>(error);
    }
}
