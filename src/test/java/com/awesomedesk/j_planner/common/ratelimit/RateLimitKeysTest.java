package com.awesomedesk.j_planner.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 요청 제한 키의 대상 해시 = 비밀 키 HMAC-SHA256 앞 16바이트 (US-41 AC2, 08 13-7) */
class RateLimitKeysTest {

    private static final String KEY = Base64.getEncoder().encodeToString("first-secret-key-for-tests-32byte".getBytes(StandardCharsets.UTF_8));
    private static final String OTHER = Base64.getEncoder().encodeToString("other-secret-key-for-tests-32byte".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("같은 키·같은 값이면 같은 32자 hex, 키가 다르면 다르다")
    void stableAndKeyed() {
        RateLimitKeys keys = new RateLimitKeys(KEY);
        assertThat(keys.hash("198.51.100.7")).hasSize(32).isEqualTo(new RateLimitKeys(KEY).hash("198.51.100.7"));
        assertThat(keys.hash("198.51.100.7")).isNotEqualTo(new RateLimitKeys(OTHER).hash("198.51.100.7"));
        assertThat(keys.hash("198.51.100.7")).isNotEqualTo(keys.hash("198.51.100.8"));
    }

    @Test
    @DisplayName("소금 없는 SHA-256 앞부분이 아니다 (IPv4·흔한 이메일을 되돌릴 수 없게)")
    void notPlainSha256() throws Exception {
        byte[] sha = MessageDigest.getInstance("SHA-256").digest("198.51.100.7".getBytes(StandardCharsets.UTF_8));
        assertThat(new RateLimitKeys(KEY).hash("198.51.100.7")).isNotEqualTo(HexFormat.of().formatHex(sha, 0, 16));
    }

    @Test
    @DisplayName("키가 32바이트보다 짧거나 base64가 아니면 켜지지 않는다. 비어 있으면(로컬) 켤 때마다 무작위 키")
    void keyRules() {
        String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> new RateLimitKeys(shortKey)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RateLimitKeys("%%% not base64 %%%")).isInstanceOf(IllegalStateException.class);
        assertThat(new RateLimitKeys("").hash("x")).isNotEqualTo(new RateLimitKeys("").hash("x"));
    }
}
