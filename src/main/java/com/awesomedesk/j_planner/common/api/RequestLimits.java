package com.awesomedesk.j_planner.common.api;

import com.awesomedesk.j_planner.common.audit.AuditEvent;
import com.awesomedesk.j_planner.common.audit.AuditLogger;
import com.awesomedesk.j_planner.common.auth.AuthUser;
import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import com.awesomedesk.j_planner.common.error.TooManyRequestsException;
import com.awesomedesk.j_planner.common.ratelimit.RateLimits;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API 요청 상한 (US-41, 08 13-7). 본문을 읽기 전(컨트롤러 전)에 막는 인터셉터 둘 (WebConfig가 붙인다).
 * 이 클래스 자체는 인터셉터가 아니다 — WebMvcTest가 인터셉터 빈을 자동으로 띄우면 Redis가 필요해져서.
 * <ul>
 *   <li>본문 1MB 초과 → 413 {@code CONTENT_TOO_LARGE} (Content-Length 기준. 앞단 프록시도 같은 상한을 둔다)</li>
 *   <li>로그인한 회원의 일반 API: 회원마다 분당 300번 ({@code api-user}) → 429 + 감사 기록. 로그인·가입 등 {@code /auth/**}는 따로 제한이 있어 빼고 붙인다(WebConfig)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RequestLimits {

    public static final long MAX_BODY_BYTES = 1024 * 1024;
    static final int USER_LIMIT_PER_MINUTE = 300;

    private final RateLimits rateLimits;
    private final AuditLogger audit;

    /** 본문 1MB — /api/** 모두에 붙인다 */
    public HandlerInterceptor bodySize() {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (request.getContentLengthLong() > MAX_BODY_BYTES) {
                    throw new ApiException(ErrorCode.CONTENT_TOO_LARGE);
                }
                return true;
            }
        };
    }

    /** 회원별 분당 300번 — /auth/**·/health를 뺀 /api/**에만 붙인다 */
    public HandlerInterceptor perUser() {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
                    return true;
                }
                try {
                    rateLimits.hit("api-user", String.valueOf(user.userId()), USER_LIMIT_PER_MINUTE, Duration.ofMinutes(1));
                } catch (TooManyRequestsException e) {
                    audit.user(user.userId(), AuditEvent.RATE_LIMITED, request, Map.of("rule", "api-user"));
                    throw e;
                }
                return true;
            }
        };
    }
}
