package com.awesomedesk.j_planner.api.v1.auth;

import com.awesomedesk.j_planner.common.audit.AuditEvent;
import com.awesomedesk.j_planner.common.audit.AuditLogger;
import com.awesomedesk.j_planner.common.auth.Reauthentication;
import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import com.awesomedesk.j_planner.common.error.FieldErrorDetail;
import com.awesomedesk.j_planner.common.error.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 비밀번호로 재인증 {@code POST /me/reauth} (08 13-4) → 이 기기에서 5분 동안 민감 작업 가능 ({@link Reauthentication}).
 * 틀린 비밀번호는 로그인 실패와 같은 계정별 대기(login-account: 5·10·20번 → 30초·5분·30분)에 센다 — 재인증으로 비밀번호를 맞혀 보지 못하게.
 * 틀리면 400 {@code REAUTH_FAILED} — 로그인 만료(401)와 구별한다 (D-085). 비밀번호가 없는 회원(구글만)도 같은 400 — 구글 재인증은 {@code /me/reauth/google}.
 */
@Service
@RequiredArgsConstructor
public class ReauthService {

    static final String WRONG_PASSWORD = "비밀번호가 맞지 않아요";

    private final JdbcTemplate jdbc;
    private final PasswordCredentials passwordCredentials;
    private final PasswordVerifier passwordVerifier;
    private final LoginRateLimiter rateLimiter;
    private final Reauthentication reauthentication;
    private final AuditLogger audit;

    public void reauth(long userId, String password, HttpServletRequest http) {
        String email = jdbc.query("SELECT email FROM users WHERE user_id = ?", rs -> rs.next() ? rs.getString(1) : null, userId);
        if (email == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        try {
            rateLimiter.checkAccount(email);
        } catch (TooManyRequestsException e) {
            audit.user(userId, AuditEvent.RATE_LIMITED, http, Map.of("rule", "login-account"));
            throw e;
        }
        if (!passwordVerifier.matches(password == null ? "" : password, passwordCredentials.findHash(userId))) {
            rateLimiter.recordFailure(email);
            throw new ApiException(ErrorCode.REAUTH_FAILED, WRONG_PASSWORD, List.of(new FieldErrorDetail("password", WRONG_PASSWORD)));
        }
        rateLimiter.recordSuccess(email);
        reauthentication.mark(http);
    }
}
