package com.awesomedesk.j_planner.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MaxChars: 유니코드 글자 단위 최대 길이")
class MaxCharsValidatorTest {

    record Title(@MaxChars(value = 3, message = "3자까지") String value) {
    }

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean ok(String value) {
        return validator.validate(new Title(value)).isEmpty();
    }

    @Test
    @DisplayName("null·빈 값·한도까지는 통과, 넘으면 실패")
    void limit() {
        assertThat(ok(null)).isTrue();
        assertThat(ok("")).isTrue();
        assertThat(ok("가나다")).isTrue();
        assertThat(ok("가나다라")).isFalse();
    }

    @Test
    @DisplayName("이모지(UTF-16 두 칸)도 한 글자로 센다 — @Size와 다른 점")
    void emojiIsOneChar() {
        assertThat(ok("😀😀😀")).isTrue();
        assertThat(ok("😀😀😀😀")).isFalse();
        assertThat(ok("a😀b")).isTrue();
    }
}
