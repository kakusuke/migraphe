package io.github.migraphe.api.common;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Railway-oriented programming の Result 型。 Sealed interface で型安全なエラーハンドリングを提供。 */
public sealed interface Result<T, E> permits Result.Ok, Result.Err {

    boolean isOk();

    boolean isErr();

    Optional<T> value();

    Optional<E> error();

    <U> Result<U, E> map(Function<T, U> mapper);

    <F> Result<T, F> mapError(Function<E, F> mapper);

    final class Ok<T, E> implements Result<T, E> {
        private final T val;

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
        public Optional<T> value() {
            return Optional.of(val);
        }

        @Override
        public Optional<E> error() {
            return Optional.empty();
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

    final class Err<T, E> implements Result<T, E> {
        private final E err;

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
        public Optional<T> value() {
            return Optional.empty();
        }

        @Override
        public Optional<E> error() {
            return Optional.of(err);
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

    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    static <T, E> Result<T, E> err(E error) {
        return new Err<>(error);
    }
}
