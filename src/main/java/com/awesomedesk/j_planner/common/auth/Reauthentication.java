package com.awesomedesk.j_planner.common.auth;

import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 재인증 (08-api-design.md 13-1·13-4): 민감 작업(구글 연결·해제 US-36, 이메일·비밀번호 변경·탈퇴 US-37) 전 <b>5분 안에</b>
 * 비밀번호를 다시 확인했는지. 확인한 시각을 로그인 세션(이 기기)에 둔다 — 다른 기기의 재인증은 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
public class Reauthentication {

    public static final Duration VALID_FOR = Duration.ofMinutes(5);
    /** 세션 속성: 재인증한 시각(밀리초) */
    static final String REAUTH_AT = "jp.reauthAt";

    private final Clock clock;

    /** 방금 재인증했다 */
    public void mark(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(REAUTH_AT, clock.millis());
        }
    }

    public boolean isFresh(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(REAUTH_AT) instanceof Long at)) {
            return false;
        }
        long now = clock.millis();
        return at <= now && now - at <= VALID_FOR.toMillis();
    }

    /** 5분 안에 재인증하지 않았으면 403 REAUTH_REQUIRED */
    public void require(HttpServletRequest request) {
        if (!isFresh(request)) {
            throw new ApiException(ErrorCode.REAUTH_REQUIRED);
        }
    }
}
