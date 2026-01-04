package io.github.migraphe.core.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.migraphe.api.common.Result;
import org.junit.jupiter.api.Test;

class ResultTest {

    @Test
    void shouldCreateOkResult() {
        // when
        Result<String, String> result = Result.ok("success");

        // then
        assertThat(result.isOk()).isTrue();
        assertThat(result.isErr()).isFalse();
        assertThat(result.value()).hasValue("success");
        assertThat(result.error()).isEmpty();
    }

    @Test
    void shouldCreateErrResult() {
        // when
        Result<String, String> result = Result.err("error message");

        // then
        assertThat(result.isOk()).isFalse();
        assertThat(result.isErr()).isTrue();
        assertThat(result.value()).isEmpty();
        assertThat(result.error()).hasValue("error message");
    }

    @Test
    void shouldMapOkValue() {
        // given
        Result<Integer, String> result = Result.ok(10);

        // when
        Result<Integer, String> mapped = result.map(x -> x * 2);

        // then
        assertThat(mapped.isOk()).isTrue();
        assertThat(mapped.value()).hasValue(20);
    }

    @Test
    void shouldNotMapErrValue() {
        // given
        Result<Integer, String> result = Result.err("error");

        // when
        Result<Integer, String> mapped = result.map(x -> x * 2);

        // then
        assertThat(mapped.isErr()).isTrue();
        assertThat(mapped.error()).hasValue("error");
    }

    @Test
    void shouldMapErrorOnErrResult() {
        // given
        Result<String, String> result = Result.err("original error");

        // when
        Result<String, Integer> mapped = result.mapError(String::length);

        // then
        assertThat(mapped.isErr()).isTrue();
        assertThat(mapped.error()).hasValue(14); // length of "original error"
    }

    @Test
    void shouldNotMapErrorOnOkResult() {
        // given
        Result<String, String> result = Result.ok("success");

        // when
        Result<String, Integer> mapped = result.mapError(String::length);

        // then
        assertThat(mapped.isOk()).isTrue();
        assertThat(mapped.value()).hasValue("success");
    }

    @Test
    void shouldThrowExceptionWhenOkValueIsNull() {
        // when & then
        assertThatThrownBy(() -> Result.ok(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("value must not be null");
    }

    @Test
    void shouldThrowExceptionWhenErrValueIsNull() {
        // when & then
        assertThatThrownBy(() -> Result.err(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("error must not be null");
    }

    @Test
    void shouldSupportPatternMatching() {
        // given
        Result<Integer, String> okResult = Result.ok(42);
        Result<Integer, String> errResult = Result.err("failed");

        // when & then
        String okMessage =
                switch (okResult) {
                    case Result.Ok<Integer, String> ok -> "Got value: " + ok.value().get();
                    case Result.Err<Integer, String> err -> "Got error: " + err.error().get();
                };
        assertThat(okMessage).isEqualTo("Got value: 42");

        String errMessage =
                switch (errResult) {
                    case Result.Ok<Integer, String> ok -> "Got value: " + ok.value().get();
                    case Result.Err<Integer, String> err -> "Got error: " + err.error().get();
                };
        assertThat(errMessage).isEqualTo("Got error: failed");
    }
}
