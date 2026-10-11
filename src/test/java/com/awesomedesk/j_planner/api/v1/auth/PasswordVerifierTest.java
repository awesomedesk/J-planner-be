package com.awesomedesk.j_planner.api.v1.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * US-34 AC1 '없는 계정도 같은 응답 시간': 비밀번호 해시가 없는(없는 계정·비밀번호 없는 계정) 경우에도
 * 미리 만든 가짜 해시로 비교를 한 번 똑같이 해서, 응답 시간으로 가입 여부를 알 수 없게 한다 — DB 없이
 */
class PasswordVerifierTest {

    /** 비교 호출 횟수를 세는 가짜 인코더 */
    private static final class CountingEncoder implements PasswordEncoder {
        int matches;

        @Override
        public String encode(CharSequence raw) {
            return "hash:" + raw;
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            matches++;
            return encoded.equals("hash:" + raw);
        }
    }

    @Test
    @DisplayName("해시가 없어도(없는 계정) 해시 비교를 한 번 하고 false")
    void unknownAccountStillCompares() {
        CountingEncoder encoder = new CountingEncoder();
        PasswordVerifier verifier = new PasswordVerifier(encoder);

        assertThat(verifier.matches("any-password-1234", null)).isFalse();
        assertThat(encoder.matches).isEqualTo(1);
    }

    @Test
    @DisplayName("해시가 있으면 그것과 비교 (한 번)")
    void knownAccount() {
        CountingEncoder encoder = new CountingEncoder();
        PasswordVerifier verifier = new PasswordVerifier(encoder);

        assertThat(verifier.matches("pw-1234567890123", "hash:pw-1234567890123")).isTrue();
        assertThat(verifier.matches("wrong", "hash:pw-1234567890123")).isFalse();
        assertThat(encoder.matches).isEqualTo(2);
    }

    @Test
    @DisplayName("가짜 해시에 어떤 비밀번호를 넣어도 맞지 않는다")
    void dummyNeverMatches() {
        PasswordVerifier verifier = new PasswordVerifier(PasswordVerifier.argon2());
        assertThat(verifier.matches("", null)).isFalse();
        assertThat(verifier.matches("password", null)).isFalse();
    }
}
