package com.awesomedesk.j_planner.common.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 로그·운영 화면의 이메일 가림 (ORCH m20261007 6번, 08 13-8 예시표와 같게. 어드민도 같은 표로 테스트).
 * 로컬 앞 2글자 + ***, 로컬이 2글자 이하면 첫 글자만. 도메인은 첫 글자 + *** + 맨 끝 마디(.com, .kr) — DB 없이
 */
class EmailMaskerTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
        "abcdef@gmail.com      | ab***@g***.com",
        "ab@gmail.com          | a***@g***.com",
        "a@naver.com           | a***@n***.com",
        "hong.gil@mail.co.kr   | ho***@m***.kr",
        "Admin@Example.COM     | ad***@e***.com",
        "noatsign              | ***",
        "@example.com          | ***",
        "user@                 | ***",
        "user@localhost        | us***@l***",
    })
    void mask(String email, String masked) {
        assertThat(EmailMasker.mask(email)).isEqualTo(masked);
    }

    @org.junit.jupiter.api.Test
    void nullIsMasked() {
        assertThat(EmailMasker.mask(null)).isEqualTo("***");
    }
}
