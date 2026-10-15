package com.awesomedesk.j_planner.api.v1.auth;

import com.awesomedesk.j_planner.common.audit.AuditEvent;
import com.awesomedesk.j_planner.common.audit.AuditLogger;
import com.awesomedesk.j_planner.common.auth.DeviceSessions;
import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import com.awesomedesk.j_planner.common.error.TooManyRequestsException;
import com.awesomedesk.j_planner.common.log.EmailMasker;
import com.awesomedesk.j_planner.common.mail.AuthMailer;
import com.awesomedesk.j_planner.common.ratelimit.RateLimits;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 비밀번호 재설정 (US-35, 08 13-3·13-7).
 * <ol>
 *   <li>찾기: 항상 202. IP별 시간당 20회·이메일별 15분 3회 안이고 회원이면 재설정 메일(이전 링크 무효). 아니면 메일만 안 보낸다</li>
 *   <li>재설정: IP별 분당 10회 → 토큰 확인(400 TOKEN_INVALID) → 새 비밀번호 규칙(400 PASSWORD_REJECTED, 링크는 그대로)
 *       → 토큰 사용 + 비밀번호 저장 → 모든 기기 로그아웃 · 로그인 실패 대기 풀기 · 감사 기록 · 알림 메일 → 204. 자동 로그인 안 함</li>
 * </ol>
 * 링크 주소는 설정 {@code app.web.base-url}만 쓴다 (요청의 Host 헤더를 쓰지 않음 — 가짜 주소로 링크를 빼돌리는 공격 방지).
 */
@Slf4j
@Service
public class PasswordResetService {

    static final Duration LINK_LIFETIME = Duration.ofMinutes(30);
    static final String RESET_PATH = "/password/reset?token=";

    private final JdbcTemplate jdbc;
    private final PasswordResetTokens tokens;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder encoder;
    private final AuthMailer mailer;
    private final RateLimits rateLimits;
    private final LoginRateLimiter loginRateLimiter;
    private final DeviceSessions deviceSessions;
    private final AuditLogger audit;
    private final Clock clock;
    private final String webBaseUrl;

    public PasswordResetService(JdbcTemplate jdbc, PasswordResetTokens tokens, PasswordPolicy passwordPolicy, PasswordEncoder encoder,
                                AuthMailer mailer, RateLimits rateLimits, LoginRateLimiter loginRateLimiter,
                                DeviceSessions deviceSessions, AuditLogger audit, Clock clock,
                                @Value("${app.web.base-url}") String webBaseUrl) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.passwordPolicy = passwordPolicy;
        this.encoder = encoder;
        this.mailer = mailer;
        this.rateLimits = rateLimits;
        this.loginRateLimiter = loginRateLimiter;
        this.deviceSessions = deviceSessions;
        this.audit = audit;
        this.clock = clock;
        this.webBaseUrl = webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
    }

    public void forgot(EmailRequest request, HttpServletRequest http) {
        String email = EmailAddress.normalize(request.email());
        if (!EmailAddress.isValid(email)) {
            throw ApiException.validation("email", "이메일 주소를 확인해 주세요.");
        }
        // TODO(US-41) 프록시 뒤 진짜 IP (getRemoteAddr)
        if (!rateLimits.tryHit("forgot-ip", http.getRemoteAddr(), 20, Duration.ofHours(1))) {
            audit.user(null, AuditEvent.RATE_LIMITED, http, Map.of("rule", "forgot-ip"));
            return;
        }
        if (!rateLimits.tryHit("forgot-email", email, 3, Duration.ofMinutes(15))) {
            audit.user(null, AuditEvent.RATE_LIMITED, http, Map.of("rule", "forgot-email"));
            return;
        }
        // TODO(US-38) 휴면 회원(dormant_profiles.email)
        List<Member> found = jdbc.query("SELECT user_id, name FROM users WHERE email = ?",
            (rs, i) -> new Member(rs.getLong("user_id"), rs.getString("name")), email);
        if (found.isEmpty()) {
            return; // 회원이 아니면 메일 없이 같은 202 (임시 가입 중인 이메일 포함)
        }
        Member member = found.getFirst();
        LocalDateTime now = now();
        String token = SignupTokens.newToken();
        tokens.issue(member.id(), SignupTokens.hash(token), now, now.plus(LINK_LIFETIME));
        try {
            mailer.sendPasswordReset(email, member.name(), webBaseUrl + RESET_PATH + token);
        } catch (RuntimeException e) {
            // TODO(US-41) jp_auth_mail_total{type=RESET,result=FAILED}
            log.error("비밀번호 재설정 메일 실패: {}", EmailMasker.mask(email), e);
        }
    }

    public void reset(PasswordResetRequest request, HttpServletRequest http) {
        // TODO(US-41) 프록시 뒤 진짜 IP (getRemoteAddr)
        try {
            rateLimits.hit("reset-ip", http.getRemoteAddr(), 10, Duration.ofMinutes(1));
        } catch (TooManyRequestsException e) {
            audit.user(null, AuditEvent.RATE_LIMITED, http, Map.of("rule", "reset-ip"));
            throw e;
        }
        String token = request.token();
        if (token == null || token.isBlank() || token.length() > 100) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        byte[] tokenHash = SignupTokens.hash(token.strip());
        LocalDateTime now = now();
        if (tokens.findUsable(tokenHash, now) == null) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        passwordPolicy.check(request.newPassword(), "newPassword");

        Long userId = tokens.useAndSetPassword(tokenHash, encoder.encode(request.newPassword()), now);
        if (userId == null) {
            throw new ApiException(ErrorCode.TOKEN_INVALID); // 그 사이 다른 요청이 먼저 씀
        }
        deviceSessions.endAll(userId);
        Map<String, Object> member = jdbc.queryForMap("SELECT email, name FROM users WHERE user_id = ?", userId);
        String email = (String) member.get("email");
        audit.user(userId, AuditEvent.PASSWORD_CHANGED, http, Map.of("method", "RESET"));
        if (email != null) {
            loginRateLimiter.recordSuccess(email); // 실패로 막혀 있던 계정도 새 비밀번호로 바로 로그인
            try {
                mailer.sendPasswordChanged(email, (String) member.get("name"));
            } catch (RuntimeException e) {
                // TODO(US-41) jp_auth_mail_total{type=NOTICE,result=FAILED}
                log.error("비밀번호 바뀜 알림 메일 실패: {}", EmailMasker.mask(email), e);
            }
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    private record Member(long id, String name) {
    }
}
