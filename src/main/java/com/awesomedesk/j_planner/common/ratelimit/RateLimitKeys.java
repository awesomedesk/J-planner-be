package com.awesomedesk.j_planner.common.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 요청 제한 Redis 키의 대상(IP·이메일·회원 번호)을 가리는 해시 (US-41, 08 13-7).
 * 비밀 키 HMAC-SHA256 앞 16바이트(32자 hex) — 소금 없는 SHA-256은 IPv4·흔한 이메일을 미리 계산해 되돌릴 수 있어서 바꿨다.
 * 키 = 설정 {@code app.ratelimit.key} (base64, 32바이트 이상; tst·prd는 {@code JP_RATELIMIT_KEY} 필수).
 * 로컬에서 비워 두면 켤 때마다 무작위 키 — 다시 켜면 요청 제한 카운터가 처음부터 센다.
 * 키를 바꾸면 남아 있던 카운터(최대 24시간)도 처음부터 센다.
 */
@Slf4j
@Component
public class RateLimitKeys {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKeySpec key;

    public RateLimitKeys(@Value("${app.ratelimit.key:}") String base64Key) {
        byte[] bytes;
        if (base64Key == null || base64Key.isBlank()) {
            bytes = new byte[MIN_KEY_BYTES];
            new SecureRandom().nextBytes(bytes);
            log.warn("요청 제한 키(app.ratelimit.key)가 없어 무작위 키를 씁니다 — 로컬 전용");
        } else {
            try {
                bytes = Base64.getDecoder().decode(base64Key.strip());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("app.ratelimit.key는 base64여야 합니다");
            }
            if (bytes.length < MIN_KEY_BYTES) {
                throw new IllegalStateException("app.ratelimit.key는 " + MIN_KEY_BYTES + "바이트 이상이어야 합니다");
            }
        }
        this.key = new SecretKeySpec(bytes, ALGORITHM);
    }

    /** 대상 값 → 32자 hex */
    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)), 0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
