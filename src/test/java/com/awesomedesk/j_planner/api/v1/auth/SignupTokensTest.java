package com.awesomedesk.j_planner.api.v1.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** US-33 AC4 인증 링크 토큰 (07 11-2): 32바이트 무작위, 주소에 넣을 수 있는 모양, DB에는 SHA-256만 — DB 없이 */
class SignupTokensTest {

    @Test
    @DisplayName("토큰은 32바이트 무작위를 base64url(패딩 없음)로 — 43자, 주소에 그대로 넣을 수 있다")
    void tokenShape() {
        String token = SignupTokens.newToken();
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    @DisplayName("토큰은 매번 다르다")
    void tokensDiffer() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(SignupTokens.newToken());
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    @DisplayName("해시는 토큰 문자열의 SHA-256 (32바이트), 같은 토큰이면 같은 해시")
    void hash() throws Exception {
        String token = SignupTokens.newToken();
        byte[] expected = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        assertThat(SignupTokens.hash(token)).hasSize(32).isEqualTo(expected).isEqualTo(SignupTokens.hash(token));
    }
}
