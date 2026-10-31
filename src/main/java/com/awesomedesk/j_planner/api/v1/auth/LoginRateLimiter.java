package com.awesomedesk.j_planner.api.v1.auth;

import com.awesomedesk.j_planner.common.error.TooManyRequestsException;
import com.awesomedesk.j_planner.common.ratelimit.RateLimitKeys;
import com.awesomedesk.j_planner.common.ratelimit.RateLimits;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 로그인 요청 횟수 제한 (Redis 카운터, 08 13-7). 키에 이메일·IP 원문 대신 비밀 키 HMAC({@link RateLimitKeys})을 쓴다 (Redis를 봐도 누군지 모르게).
 * <ul>
 *   <li>IP별: 첫 시도부터 1분 동안 20회까지 ({@link RateLimits} 규칙 {@code login-ip}). 시계의 '분'으로 자르면 분이 바뀔 때 다시 0부터라 US-41에서 바꿈</li>
 *   <li>계정(이메일)별: 실패 수 {@code jp:rl:login-fail:<해시>}(마지막 실패 뒤 24시간 유지, 성공하면 지움),
 *       기다릴 시간 {@code jp:rl:login-wait:<해시>}(TTL = {@link LoginBackoff}). 없는 계정도 똑같이 센다</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class LoginRateLimiter {

    static final int IP_LIMIT_PER_MINUTE = 20;
    private static final Duration FAILURE_MEMORY = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final RateLimits rateLimits;
    private final RateLimitKeys keys;

    /** IP 한도를 넘었으면 429 (Retry-After = 창이 끝날 때까지). 시도마다 1 센다 */
    void countIp(String ip) {
        rateLimits.hit("login-ip", ip, IP_LIMIT_PER_MINUTE, Duration.ofMinutes(1));
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

    private String failKey(String email) {
        return "jp:rl:login-fail:" + keys.hash(email);
    }

    private String waitKey(String email) {
        return "jp:rl:login-wait:" + keys.hash(email);
    }
}
