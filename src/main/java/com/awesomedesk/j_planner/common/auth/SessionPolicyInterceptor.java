package com.awesomedesk.j_planner.common.auth;

import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 로그인 세션 규칙 (08-api-design.md 13-1). 세션으로 들어온 요청마다:
 * <ul>
 *   <li>로그인부터 30일이 지났으면 세션을 지우고 401 (쓰고 있어도 연장 없음)</li>
 *   <li>1분마다 회원을 다시 읽어 정지(suspended_at, D-069)거나 없어졌으면 세션을 지우고 401 —
 *       어드민의 세션 삭제와 별도로 두는 안전장치 (ORCH 요청)</li>
 * </ul>
 * 인증 API({@code /api/v1/auth/**})는 거치지 않는다 (만료된 세션으로도 다시 로그인할 수 있게).
 * 빈은 {@code SessionConfig}에서 만든다 (컨트롤러만 띄우는 WebMvcTest에 끌려 들어가지 않게).
 */
@RequiredArgsConstructor
public class SessionPolicyInterceptor implements HandlerInterceptor {

    static final Duration MAX_LIFETIME = Duration.ofDays(30);
    static final Duration STATUS_CHECK_INTERVAL = Duration.ofMinutes(1);

    private final JdbcTemplate jdbc;
    private final DeviceSessions deviceSessions;
    private final Clock clock;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(DeviceSessions.LOGIN_AT) instanceof Long loginAt)) {
            return true; // 세션 없이 들어온 요청 (로그인 안 함 → Spring Security가 이미 401)
        }
        long now = clock.millis();
        if (now - loginAt >= MAX_LIFETIME.toMillis()) {
            throw endSession(request);
        }
        Object checkedAt = session.getAttribute(DeviceSessions.STATUS_CHECKED_AT);
        if (!(checkedAt instanceof Long checked) || now - checked >= STATUS_CHECK_INTERVAL.toMillis()) {
            long userId = AuthUserArgumentResolver.current().userId();
            List<Boolean> suspended = jdbc.queryForList("SELECT suspended_at IS NOT NULL FROM users WHERE user_id = ?", Boolean.class, userId);
            if (suspended.isEmpty() || suspended.getFirst()) {
                throw endSession(request);
            }
            session.setAttribute(DeviceSessions.STATUS_CHECKED_AT, now);
        }
        return true;
    }

    private ApiException endSession(HttpServletRequest request) {
        deviceSessions.end(request);
        return new ApiException(ErrorCode.UNAUTHENTICATED);
    }
}
