package com.awesomedesk.j_planner.api.v1.auth;

import com.awesomedesk.j_planner.common.audit.AuditEvent;
import com.awesomedesk.j_planner.common.audit.AuditLogger;
import com.awesomedesk.j_planner.common.auth.DeviceSessions;
import com.awesomedesk.j_planner.common.auth.Reauthentication;
import com.awesomedesk.j_planner.common.log.EmailMasker;
import com.awesomedesk.j_planner.common.mail.AuthMailer;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 비밀번호 변경·추가 {@code PUT /me/password} (US-37, 08 13-4).
 * 재인증 5분(403) → 가입과 같은 규칙(400 PASSWORD_REJECTED, errors newPassword — 유출 확인 포함, D-076) → 저장
 * → 이 기기는 그대로·다른 기기 로그아웃 → 로그인 실패 대기 풀기 → PASSWORD_CHANGED {method: CHANGE} + 알림 메일 → 204.
 * 비밀번호가 없던 회원(구글만, admin 첫 설정 전)은 '추가'가 된다 (구글 재인증 뒤).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private final JdbcTemplate jdbc;
    private final Reauthentication reauthentication;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder encoder;
    private final PasswordCredentials passwordCredentials;
    private final LoginRateLimiter loginRateLimiter;
    private final DeviceSessions deviceSessions;
    private final AuditLogger audit;
    private final AuthMailer mailer;
    private final Clock clock;

    public void change(long userId, String newPassword, HttpServletRequest http) {
        reauthentication.require(http);
        passwordPolicy.check(newPassword, "newPassword");
        passwordCredentials.setEncoded(userId, encoder.encode(newPassword), LocalDateTime.now(clock).withNano(0));

        String current = http.getSession(false) == null ? "" : http.getSession(false).getId();
        deviceSessions.endOthers(userId, current);
        audit.user(userId, AuditEvent.PASSWORD_CHANGED, http, Map.of("method", "CHANGE"));
        Map<String, Object> member = jdbc.queryForMap("SELECT email, name FROM users WHERE user_id = ?", userId);
        String email = (String) member.get("email");
        if (email != null) {
            loginRateLimiter.recordSuccess(email);
            try {
                mailer.sendPasswordChanged(email, (String) member.get("name"));
            } catch (RuntimeException e) {
                log.error("비밀번호 바뀜 알림 메일 실패: {}", EmailMasker.mask(email), e);
            }
        }
    }
}
