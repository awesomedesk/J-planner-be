package com.awesomedesk.j_planner.api.v1.auth;

import com.awesomedesk.j_planner.common.error.TooManyRequestsException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 로그인 요청 횟수 제한 (Redis 카운터, 08 13-7). 키에 이메일·IP 원문 대신 SHA-256 앞부분을 쓴다 (Redis를 봐도 누군지 모르게).
 * <ul>
 *   <li>IP별: 1분 창에 20회까지 ({@code jp:rl:login-ip:<해시>:<분>})</li>
 *   <li>계정(이메일)별: 실패 수 {@code jp:rl:login-fail:<해시>}(마지막 실패 뒤 24시간 유지, 성공하면 지움),
 *       기다릴 시간 {@code jp:rl:login-wait:<해시>}(TTL = {@link LoginBackoff}). 없는 계정도 똑같이 센다</li>
 * </ul>
 * TODO(US-41) 소금 없는 SHA-256은 IPv4·흔한 이메일을 되돌릴 수 있다 → 비밀 키(환경 변수) HMAC-SHA256으로 바꾼다 (PO 제안, m20261014-po-be-us34-code)
 */
@Component
@RequiredArgsConstructor
class LoginRateLimiter {

    static final int IP_LIMIT_PER_MINUTE = 20;
    private static final Duration FAILURE_MEMORY = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    /** IP 한도를 넘었으면 429. 시도마다 1 센다 */
    void countIp(String ip) {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = "jp:rl:login-ip:" + hash(ip) + ":" + minute;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofSeconds(70));
        }
        if (count != null && count > IP_LIMIT_PER_MINUTE) {
            throw new TooManyRequestsException(ErrorMessages.TOO_MANY, 60 - Instant.now().getEpochSecond() % 60);
        }
    }

    /** 계정이 기다리는 중이면 429 (+ 로그인 실패와 같은 문구) */
    void checkAccount(String email) {
        Long ttl = redis.getExpire(waitKey(email));
        if (ttl != null && ttl > 0) {
            throw new TooManyRequestsException(ErrorMessages.LOGIN_FAILED, ttl);
        }
    }

    void recordFailure(String email) {
        String failKey = failKey(email);
        Long failures = redis.opsForValue().increment(failKey);
        redis.expire(failKey, FAILURE_MEMORY);
        long wait = LoginBackoff.waitSeconds(failures == null ? 0 : failures.intValue());
        if (wait > 0) {
            redis.opsForValue().set(waitKey(email), "1", Duration.ofSeconds(wait));
        }
    }

    void recordSuccess(String email) {
        redis.delete(java.util.List.of(failKey(email), waitKey(email)));
    }

    private static String failKey(String email) {
        return "jp:rl:login-fail:" + hash(email);
    }

    private static String waitKey(String email) {
        return "jp:rl:login-wait:" + hash(email);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
