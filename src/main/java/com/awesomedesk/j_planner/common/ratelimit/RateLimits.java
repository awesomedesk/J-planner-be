package com.awesomedesk.j_planner.common.ratelimit;

import com.awesomedesk.j_planner.common.error.TooManyRequestsException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 요청 횟수 제한 카운터 (Redis, 08 13-7). 키 {@code jp:rl:<규칙>:<대상 해시>}, 첫 요청부터 {@code window} 동안 센다 (고정 창).
 * 대상(IP·이메일)은 원문 대신 SHA-256 앞부분 (Redis를 봐도 누군지 모르게).
 * TODO(US-41) 소금 없는 SHA-256 → 비밀 키 HMAC-SHA256 (LoginRateLimiter와 같이)
 */
@Component
@RequiredArgsConstructor
public class RateLimits {

    static final String MESSAGE = "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.";

    private final StringRedisTemplate redis;

    /** 한 번 센다. {@code limit}을 넘었으면 429 (Retry-After = 창이 끝날 때까지) */
    public void hit(String rule, String subject, int limit, Duration window) {
        String key = key(rule, subject);
        if (count(key, window) > limit) {
            Long ttl = redis.getExpire(key);
            throw new TooManyRequestsException(MESSAGE, ttl == null || ttl <= 0 ? window.toSeconds() : ttl);
        }
    }

    /** 한 번 센다. {@code limit} 안이면 true (넘으면 false — 화면은 그대로 두고 일만 하지 않을 때) */
    public boolean tryHit(String rule, String subject, int limit, Duration window) {
        return count(key(rule, subject), window) <= limit;
    }

    private long count(String key, Duration window) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, window);
        }
        return count == null ? 0 : count;
    }

    private static String key(String rule, String subject) {
        return "jp:rl:" + rule + ":" + hash(subject);
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
